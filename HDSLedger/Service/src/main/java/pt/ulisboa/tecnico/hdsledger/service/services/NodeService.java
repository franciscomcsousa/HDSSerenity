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
    // Link to communicate with clients
    private final Link clientLink;

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
    private final int timerMillis = 5000;
    
    // Consensus instance to which the timer is counting
    private int timerInstance = -1;

    // Ledger (for now, just a list of strings)
    private ArrayList<String> ledger = new ArrayList<String>();

    public NodeService(Link link, Link clientLink, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig) {

        this.link = link;
        this.clientLink = clientLink;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        //Only count nodes that are not clients
        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.roundChangeMessages = new MessageBucket(nodesConfig.length);
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

    // Overload
    public ConsensusMessage createConsensusMessage(String value, int instance, int round, String replyTo) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .setReplyTo(replyTo)
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
    public void startConsensus(ClientMessage message) {
        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(message.getMessage()));

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

        // DIFF VALUE BYZANTINE TEST
        if (config.getBehavior() == ProcessConfig.Behavior.DIFF_VALUE) {
            message.setMessage("DIFFERENT VALUE");
        }

        // Leader broadcasts PRE-PREPARE message
        if (this.config.isLeader()) {
            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));

            ConsensusMessage m = this.createConsensusMessage(message.getMessage(), localConsensusInstance,
                instance.getCurrentRound(), message.getSenderId());
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
    public void uponPrePrepare(ConsensusMessage message) {

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

        // DIFFERENT PREPARE VALUE BYZANTINE TEST
        if (config.getBehavior() == ProcessConfig.Behavior.PREPARE_VALUE) {
            prepareMessage = new PrepareMessage("DIFFERENT VALUE");
        }

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

            // DIFFERENT COMMIT VALUE BYZANTINE TEST
            if (config.getBehavior() == ProcessConfig.Behavior.COMMIT_VALUE) {
                c = new CommitMessage("DIFFERENT VALUE");
            }

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
            int position = ledger.size();
            ClientMessage clientMessage = new ClientMessage(config.getId(), Message.Type.RESPONSE);
            clientMessage.setMessage(value);
            clientMessage.setPosition(position);

            // Respond to the clients
            clientLink.broadcastToClients(clientMessage);
            
            // Cancels the timer of the consensus when a quorum of commits
            // is acquired
            timerConsensus.cancel();
            timerConsensus.purge();
        }
    }

    /*
     * Check if a PrePrepare is justified
     *
     */
    // TODO - use this
    public boolean justifyPrePrepare(String nodeId, int instance, int round, List<ConsensusMessage> justification){
        return round == 1 || this.justifyRoundChange(nodeId, instance, round, justification);
    }

    /*
    * Check if a Round Change is justified
    *
     */
    public boolean justifyRoundChange(String nodeId, int instance, int round, List<ConsensusMessage> justificationList){
        // If for all round changes messages, none have prepared a value, round change is justified
        if (roundChangeMessages.nonePreparedJustification(instance, round)) {
            return true;
        }

        // Justification messages is the set of the received Prepared messages
        // piggybacked to the Round-Change message
        MessageBucket justificationMessages = new MessageBucket(nodesConfig.length);
        for (ConsensusMessage message : justificationList) {
            justificationMessages.addMessage(message);
        }

        Optional<String> prepareQuorumValue = Optional.empty();
        Optional<RoundChangeMessage> highestRoundChangeMessage = roundChangeMessages.highestPrepared(instance, round);
        // Check if the received Justification messages quorum value matches the Round-Change quorum proposed value
        if (highestRoundChangeMessage.isPresent())
            prepareQuorumValue = justificationMessages.hasValidPrepareQuorum(
                    nodeId,
                    instance,
                    highestRoundChangeMessage.get().getPreparedRound());

        // TODO - Check signatures of the messages

        return prepareQuorumValue.isPresent() &&
                prepareQuorumValue.get().equals(highestRoundChangeMessage.get().getPreparedValue());
    }

    /*
     * Handle roundChange messages and decide if there is a valid quorum
     * @param message ConsensusMessage to be handled
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

        // TODO - Verify if it has received f + 1, ROUND_CHANGE messages - not essential but increases liveness
        // if it has, broadcasts the message to all,
        // updates the round value
        Optional<String> roundChangeValue;

        // Verify if it has received Quorum, ROUND_CHANGE messages
        // if it has, JustifyRoundChange
        roundChangeValue = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round);

        List<ConsensusMessage> receivedJustification = message.getJustification();

        // TODO - verify if upon rule is only triggered once per round

        if (roundChangeValue.isPresent() &&
                instance.getPreparedRound() < round &&
                justifyRoundChange(config.getId(), consensusInstance, round, receivedJustification)) {

            System.out.println("ROUND CHANGE QUORUM RECEIVED");

            // Update the leader of the consensus
            // (remove the old leader and make the one with the id of the previous leader + 1 the new leader)
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

        // This needs to be either a string or an empty string, if this were to be null,
        // it would be mistaken for the null return of some <Optional>String return type functions
        String preparedValue = existingConsensus.getPreparedValue() != null ? existingConsensus.getPreparedValue() : "";
        int preparedRound = existingConsensus.getPreparedValue() != null ? existingConsensus.getCurrentRound() : -1;

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

        // Add any Prepare messages as justification
        Optional<List<ConsensusMessage>> justificationMessages =
                prepareMessages.getPrepareMessages(config.getId(), consensusInstance.get(), round);

        if (justificationMessages.isPresent()) {
            consensusMessage.setJustification(justificationMessages.get());
        }

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

                                case INVALID ->
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Received INVALID message from {1}",
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
