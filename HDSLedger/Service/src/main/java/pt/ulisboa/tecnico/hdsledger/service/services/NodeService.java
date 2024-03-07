package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    // Nodes configurations
    private final ProcessConfig[] nodesConfig;

    // Current node is leader
    private final ProcessConfig config;
    // Leader configuration
    private ProcessConfig leaderConfig;

    // Link to communicate with nodes
    private final Link link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;
    // Consensus instance -> Round -> List of roundChange messages
    private final MessageBucket roundChangeMessages;

    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    // Timer to trigger round changes
    private Timer timerConsensus;

    // Consensus should take max timerMilliseconds
    private final int timerMillis = 2000;
    
    // Consensus instance to which the timer is counting
    private int timerInstance = -1;

    // Ledger (for now, just a list of strings)
    private ArrayList<String> ledger = new ArrayList<String>();

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig) {

        this.link = link;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        //Only count nodes that are not clients
        this.prepareMessages = new MessageBucket(Arrays.stream(nodesConfig)
            .filter(c -> Integer.parseInt(c.getId()) < 20 && Integer.parseInt(c.getId()) > 0)
            .toArray(ProcessConfig[]::new).length);
        this.commitMessages = new MessageBucket(Arrays.stream(nodesConfig)
        .filter(c -> Integer.parseInt(c.getId()) < 20 && Integer.parseInt(c.getId()) > 0)
        .toArray(ProcessConfig[]::new).length);
        this.roundChangeMessages = new MessageBucket(Arrays.stream(nodesConfig)
        .filter(c -> Integer.parseInt(c.getId()) < 20 && Integer.parseInt(c.getId()) > 0)
        .toArray(ProcessConfig[]::new).length);
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<String> getLedger() {
        return this.ledger;
    }

    private boolean isLeader(String id) {
        return this.leaderConfig.getId().equals(id);
    }
 
    public ConsensusMessage createConsensusMessage(String value, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }

    /*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public void startConsensus(String message) throws Exception {
        System.out.println("CONSENSUS STARTED!");
        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(message));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return;
        }

        // Only start a consensus instance if the last one was decided
        // We need to be sure that the previous value has been decided
        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Leader broadcasts PRE-PREPARE message
        if (this.config.isLeader()) {
            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));

            ConsensusMessage m = this.createConsensusMessage(message, localConsensusInstance, instance.getCurrentRound());
            this.link.broadcast(m);

        } else {
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }
        // Set the timer of a new consensus for the leader
        // and a call for the RoundTimer class
        if (timerConsensus != null) {
        timerConsensus.cancel();
        timerConsensus.purge();
        }

        this.timerConsensus = new Timer();
        timerConsensus.schedule(new Node.RoundTimer(), timerMillis);
        timerInstance = localConsensusInstance;
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified then broadcast prepare
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) throws Exception {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        String value = prePrepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Verify if pre-prepare was sent by leader
        if (!isLeader(senderId))
            return;

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

        this.link.broadcast(consensusMessage);

        // Set the timer of a new consensus for the node
        // and a call for the RoundTimer class
        if (!senderId.equals(config.getId())) {
            timerConsensus.cancel();
            timerConsensus.purge();

            this.timerConsensus = new Timer();
            timerConsensus.schedule(new Node.RoundTimer(), timerMillis);
            timerInstance = consensusInstance;
        }
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(ConsensusMessage message) throws Exception {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        String value = prepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getPreparedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setReplyTo(senderId)
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(instance.getCommitMessage().toJson())
                    .build();

            link.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
            instance.setPreparedValue(preparedValue.get());
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(preparedValue.get());
            instance.setCommitMessage(c);

            for (ConsensusMessage senderMessage : sendersMessage) {
                ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setReplyTo(senderMessage.getSenderId())
                        .setReplyToMessageId(senderMessage.getMessageId())
                        .setMessage(c.toJson())
                        .build();

                link.send(senderMessage.getSenderId(), m);
            }
        }
    }



    /*
     * Handle commit messages and decide if there is a valid quorum
     *
     * @param message Message to be handled
     */
    public synchronized void uponCommit(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            MessageFormat.format(
                    "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round);
            return;
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            String value = commitValue.get();

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized(ledger) {

                // Increment size of ledger to accommodate current instance
                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add("");
                }
                
                ledger.add(consensusInstance - 1, value);
                
                LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Current Ledger: {1}",
                            config.getId(), String.join("", ledger)));
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));

            // Broadcast to all the clients for now
            // Position of the new value in the ledger
            int position = ledger.size();
            ResponseMessage responseMessage = new ResponseMessage(config.getId(), Message.Type.RESPONSE, value, position);
            link.broadcastToClients(responseMessage);
            
            // Cancels the timer of the consensus when a quorum of commits
            // is acquired
            timerConsensus.cancel();
            timerConsensus.purge();
        }
    }

    /*
     * Handle roundChange messages and decide if there is a valid quorum
     * @param message Message to be handled
     */
    public synchronized void uponRoundChange(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received ROUND-CHANGE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // verifies if the round change is bigger than the process current round
        if (instance.getCurrentRound() < round)
            return;

        roundChangeMessages.addMessage(message);

        // TODO (again) - Verify if it has received f + 1, ROUND_CHANGE messages
        // if it has, broadcasts the message to all,
        // updates the round value
        Optional<String> roundChangeValue;

        // Verify if it has received Quorum, ROUND_CHANGE messages
        // if it has, TODO - JustifyRoundChange
        roundChangeValue = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round);
        System.out.println("\nROUND CHANGE VALUE: " + roundChangeValue + "\n");
        if (roundChangeValue.isPresent() && instance.getPreparedRound() < round) {
            System.out.println("ROUND CHANGE QUORUM RECEIVED!");

            // Update the leader of the consensus (remove the old leader and make the one with the id of the previous leader + 1 the new leader)
            Arrays.stream(nodesConfig).forEach(
                    processConfig -> { if (isLeader(processConfig.getId())) processConfig.setLeader(false); }
            );

            int currentLeaderId = Integer.parseInt(leaderConfig.getId());
            String newLeaderId;

            if (currentLeaderId + 1 > Arrays.stream(nodesConfig)
                    .filter(processConfig -> Integer.parseInt(processConfig.getId()) < 20)
                    .count())
                newLeaderId = "1";
            else
                newLeaderId = Integer.toString(currentLeaderId + 1);

            Arrays.stream(nodesConfig).forEach(
                    processConfig -> { if (processConfig.getId().equals(newLeaderId)) processConfig.setLeader(true); }
            );

            leaderConfig = Arrays.stream(nodesConfig)
                    .filter(processConfig -> processConfig.isLeader())
                    .findFirst().get();

            // If it's the leader, start a new consensus by broadcasting a PRE-PREPARE message
            // The value of the new consensus is the highest prepared value of the Quorum if it exists,
            // otherwise the value is the one passed as input to this instance
            if (config.isLeader()) {
                String value = instance.getPreparedValue();
                if (value == null) {
                    value = instance.getInputValue();
                }
                // Start a new consensus by broadcasting a PRE-PREPARE message
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));

                ConsensusMessage m = this.createConsensusMessage(value, consensusInstance, instance.getCurrentRound());

                this.link.broadcast(m);
            }
            else {
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
            }
            // Reset the timer of the consensus for this node
            // because it is trying for a new round
            this.timerConsensus.cancel();
            this.timerConsensus.purge();

            this.timerConsensus = new Timer();
            timerConsensus.schedule(new Node.RoundTimer(), timerMillis);
        }
    }

    /*
     * Timer has expired, send a request for a round change to the others
     */
    public synchronized void uponTimerExpiry() {
        /*  ri ← ri + 1
            set timeri to running and expire after t(ri)
            broadcast 〈ROUND-CHANGE, λi, ri, pri, pvi〉
        */

        InstanceInfo existingConsensus = this.instanceInfo.get(timerInstance);

        /*if (existingConsensus == null) {
            // TODO
            // not sure WHY the timer goes out before the consensus is initiated ???
            return;
        }*/

        // TODO - are both of these right?
        int preparedRound = existingConsensus.getCurrentRound();
        // This needs to be either a string or an empty string, if this were to be null,
        // it would be mistaken for the null return of some <Optional>String return type functions
        String preparedValue = existingConsensus.getPreparedValue() != null ? existingConsensus.getPreparedValue() : "";

        // Increment the round in the instanceInfo of the node
        existingConsensus.incrementCurrentRound();
        this.instanceInfo.put(timerInstance, existingConsensus);

        // just to be clearer to read
        String value = existingConsensus.getInputValue();
        int round = existingConsensus.getCurrentRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Broadcast ROUND_CHANGE message: Consensus Instance {1}, NOW Round {2}",
                        config.getId(), consensusInstance, round));

        RoundChangeMessage roundChangeMessage = new RoundChangeMessage(preparedRound, preparedValue);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(timerInstance)
                .setRound(round)
                .setMessage(roundChangeMessage.toJson())
                .build();

        this.link.broadcast(consensusMessage);

        // Reset the timer of the consensus for this node
        // because it is trying for a new round
        this.timerConsensus.cancel();
        this.timerConsensus.purge();

        this.timerConsensus = new Timer();
        timerConsensus.schedule(new Node.RoundTimer(), timerMillis);
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {

                        Message message = link.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {
                                case APPEND ->  // placeholder
                                {
                                    try {
                                        startConsensus(((RequestMessage) message).getMessage());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                case PRE_PREPARE -> {
                                    try {
                                        uponPrePrepare((ConsensusMessage) message);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                case PREPARE -> {
                                    try {
                                        uponPrepare((ConsensusMessage) message);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                case COMMIT -> {
                                    try {
                                        uponCommit((ConsensusMessage) message);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                case ROUND_CHANGE -> {
                                    try {
                                        uponRoundChange((ConsensusMessage) message);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                            config.getId(), message.getSenderId()));

                                case IGNORE ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    config.getId(), message.getSenderId()));

                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));
                            }

                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
