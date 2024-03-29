package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.models.*;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSASignature;

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

    private ArrayList<Block> ledger = new ArrayList<Block>();

    // Client Balances
    private final Map<String, Integer> clientsBalance = new ConcurrentHashMap<>();

    // Auxiliary client balances for checking if the client has enough money to do a transaction
    private final Map<String, Integer> auxClientsBalance = new ConcurrentHashMap<>();

    // Transfer Requests Queue
    private final List<String> transactionRequests = new LinkedList<>();

    public NodeService(Link link, Link clientLink, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig, ProcessConfig[] clientConfigs) {

        this.link = link;
        this.clientLink = clientLink;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        //Only count nodes that are not clients
        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.roundChangeMessages = new MessageBucket(nodesConfig.length);

        // Update Map with clients IDs and respective balances
        Arrays.stream(clientConfigs).forEach(client -> this.clientsBalance.put(client.getId(), 10));
        Arrays.stream(clientConfigs).forEach(client -> this.auxClientsBalance.put(client.getId(), 10));
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<Block> getLedger() {
        return this.ledger;
    }

    private boolean isLeader(String id) {
        return this.leaderConfig.getId().equals(id);
    }
 
    public ConsensusMessage createConsensusMessage(Block block, int instance, int round) {
        String blockToJson = block.toJson();
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(blockToJson);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }

    public Block createBlock (String nodeId) {
        Block newBlock = new Block();

        // Add to block the first n elements of transactionRequests
        // Does not delete the elements
        List<String> transactionsJson = transactionRequests.stream().limit(newBlock.getMaxBlockSize()).toList();
        for (String transaction : transactionsJson) {
            newBlock.addTransaction(Transaction.fromJson(transaction));
        }

        newBlock.setNodeId(nodeId);
        return newBlock;
    }

    // Get the balance of a specific client passed as an argument
    public int getBalance(String clientId) {
        return clientsBalance.get(clientId);
    }

    /**
     * Adds new Transaction to the Requests
     * Verifies if there are enough Transactions to create a block
     * If a block can be created, start a consensus
     *
     * @param transaction New Transaction added to the Queue
     */
    public void newTransferRequest(Transaction transaction) {

        // TODO - more Transaction verification needed

        // Verifies if client has enough money to do that transaction
        if (clientsBalance.get(transaction.getSender()) < transaction.getAmount() || 
        auxClientsBalance.get(transaction.getSender()) < transaction.getAmount()) {
            auxClientsBalance.forEach((key, value) -> auxClientsBalance.put(key, clientsBalance.get(key)));
            TResponseMessage tResponseMessage = new TResponseMessage(transaction.toJson(),  TResponseMessage.Status.FAILED_BALANCE);
            ClientMessage clientMessage = new ClientMessage(config.getId(), Message.Type.TRANSFER_RESPONSE);
            clientMessage.setMessage(tResponseMessage.toJson());

            clientLink.send(transaction.getSender(), clientMessage);
            return;
        }

        // Adds the transaction to the Queue
        transactionRequests.add(transaction.toJson());
        auxClientsBalance.put(transaction.getSender(), clientsBalance.get(transaction.getSender()) - transaction.getAmount());

        Block newBlock = new Block();

        // if there are enough Transactions for a block startConsensus
        // TODO maybe change so it doesnt create a useless block
        if (transactionRequests.size() == newBlock.getMaxBlockSize()) {
            // Reset the auxClientsBalance
            auxClientsBalance.forEach((key, value) -> auxClientsBalance.put(key, clientsBalance.get(key)));
            startConsensus();
        }
    }


    /**
     * Start an instance of consensus for a new block
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     */
    public void startConsensus() {

        // Create a new Block
        Block block = createBlock(this.config.getId());

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(block));

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
            block = new Block();

            // Add to block different made up transactions
            for (int i = 0; i < block.getMaxBlockSize(); i++) {
                Transaction transaction = new Transaction("20", "21", 100, -1);
                block.addTransaction(transaction);
            }

            block.setNodeId(this.config.getId());
        }

        // Leader broadcasts PRE-PREPARE message
        if (this.config.isLeader()) {
            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));

            ConsensusMessage m = this.createConsensusMessage(block, localConsensusInstance, instance.getCurrentRound());
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

        Block block = Block.fromJson(prePrepareMessage.getBlock());

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Verify if Block created by the leader has valid transactions
        for (Transaction transaction : block.getTransactions()) {
            if (!transactionRequests.contains(transaction.toJson()))
                return;
        }


        // Verify if pre-prepare was sent by leader
        if (!isLeader(senderId))
            return;

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));

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

        if (!justifyPrePrepare(config.getId(), consensusInstance, round, message.getJustification())) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Received UNJUSTIFIED PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, ignoring",
                            config.getId(), senderId, consensusInstance, round));
            return;
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getBlock());

        // DIFFERENT PREPARE VALUE BYZANTINE TEST
        if (config.getBehavior() == ProcessConfig.Behavior.PREPARE_VALUE) {
            // Create a prepare message with a different block
            Block differentBlock = new Block();

            for (int i = 0; i < differentBlock.getMaxBlockSize(); i++) {
                Transaction transaction = new Transaction("20", "21", 100, -1);
                differentBlock.addTransaction(transaction);
            }

            prepareMessage = new PrepareMessage(differentBlock.toJson());
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
        if (!senderId.equals(config.getId()) && timerConsensus != null) {
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

        Block block = Block.fromJson(prepareMessage.getBlock());

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));
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
        Optional<String> preparedBlock = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedBlock.isPresent() && instance.getPreparedRound() < round) {

            Block quorumBlock = Block.fromJson(preparedBlock.get());

            instance.setPreparedBlock(quorumBlock);
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(preparedBlock.get());

            // DIFFERENT COMMIT VALUE BYZANTINE TEST
            if (config.getBehavior() == ProcessConfig.Behavior.COMMIT_VALUE) {
                // Create a commit message with a different block
                Block differentBlock = new Block();

                for (int i = 0; i < differentBlock.getMaxBlockSize(); i++) {
                    Transaction transaction = new Transaction("20", "21", 100, -1);
                    differentBlock.addTransaction(transaction);
                }

                c = new CommitMessage(differentBlock.toJson());
            }

            instance.setCommitMessage(c);

            // NO COMMIT BYZANTINE TEST
            if (config.getBehavior() == ProcessConfig.Behavior.NO_COMMIT) {
                // Return without sending commit messages only for the first round
                if (round == 1) {
                    return;
                }
            }

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

        CommitMessage commitMessage = message.deserializeCommitMessage();

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

        Optional<String> commitBlock = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);

        if (commitBlock.isPresent() && instance.getCommittedRound() < round) {

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            Block blockToLedger = Block.fromJson(commitBlock.get());

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized(ledger) {

                // Increment size of ledger to accommodate current instance
                // TODO - do not add a block without checking if its sequential
                ledger.ensureCapacity(consensusInstance);
//                while (ledger.size() < consensusInstance - 1) {
//                    ledger.add("");
//                }
                
                ledger.add(consensusInstance - 1, blockToLedger);
                
                LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Current Ledger: {1}",
                            config.getId(), this.ledger));
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));

            int position = ledger.size();

            Block committedBlock = Block.fromJson(commitMessage.getBlock());
            
            // Update client balances and send TransferResponse for each transaction in the block committed
            for (Transaction transaction : committedBlock.getTransactions()) {
                // Update the balances of the clients
                synchronized(clientsBalance) {
                    clientsBalance.put(transaction.getSender(), clientsBalance.get(transaction.getSender()) - transaction.getAmount());
                    clientsBalance.put(transaction.getReceiver(), clientsBalance.get(transaction.getReceiver()) + transaction.getAmount());
                    auxClientsBalance.forEach((key, value) -> auxClientsBalance.put(key, clientsBalance.get(key)));
                }

                String senderId = transaction.getSender();
                TResponseMessage transferResponse = new TResponseMessage(transaction.toJson(), TResponseMessage.Status.SUCCESS);
                transferResponse.setPosition(position);

                ClientMessage clientMessage = new ClientMessage(config.getId(), Message.Type.TRANSFER_RESPONSE);
                clientMessage.setMessage(transferResponse.toJson());

                // Respond to the client
                clientLink.send(senderId, clientMessage);

                // removes the transactions committed from the transactionsRequest
                transactionRequests.remove(transaction.toJson());
            }

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
    public boolean justifyPrePrepare(String nodeId, int instance, int round, List<ConsensusMessage> justification) throws Exception {
        return round == 1 || this.justifyRoundChange(nodeId, instance, round, justification);
    }

    /*
    * Check if a Round Change is justified
    *
     */
    public boolean justifyRoundChange(String nodeId, int instance, int round, List<ConsensusMessage> justificationList) throws Exception {
        // If for all round changes messages, none have prepared a value, round change is justified
        if (roundChangeMessages.nonePreparedJustification(instance, round)) {
            return true;
        }

        // Justification messages is the set of the received Prepared messages
        // piggybacked to the Round-Change message
        MessageBucket justificationMessages = new MessageBucket(nodesConfig.length);
        byte[] signature = null;
        for (ConsensusMessage message : justificationList) {
            signature = message.getSignature();
            // Check signature validity for each justification message
            if (RSASignature.verifySign(message.getSignable(), signature, message.getSenderId())){
                justificationMessages.addMessage(message);
            }
            else {
                LOGGER.log(Level.INFO,
                        MessageFormat.format(
                                "{0} - Invalid signature in justification for Instance {1}, Round {2}, ignoring",
                                config.getId(), consensusInstance, round));
                return false;
            }
        }

        Optional<String> prepareQuorumValue = Optional.empty();
        Optional<RoundChangeMessage> highestRoundChangeMessage = roundChangeMessages.highestPrepared(instance, round);
        // Check if the received Justification messages quorum value matches the Round-Change quorum proposed value
        if (highestRoundChangeMessage.isPresent())
            prepareQuorumValue = justificationMessages.hasValidPrepareQuorum(
                    nodeId,
                    instance,
                    highestRoundChangeMessage.get().getPreparedRound());

        return prepareQuorumValue.isPresent() &&
                prepareQuorumValue.get().equals(highestRoundChangeMessage.get().getPreparedValue());
    }

    /*
     * Handle roundChange messages and decide if there is a valid quorum
     * @param message ConsensusMessage to be handled
     */
    public synchronized void uponRoundChange(ConsensusMessage message) throws Exception {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received ROUND-CHANGE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        roundChangeMessages.addMessage(message);
        Optional<String> roundChangeQuorum;

        // Any upon rule can be triggered at most once per round
        if (instance.getLatestRoundChangeBroadcast() >= round ) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already broadcast ROUND-CHANGE for Consensus Instance {1}, Round {2}, won't do it again",
                            config.getId(), consensusInstance, round));
        }
        else {
            roundChangeQuorum = roundChangeMessages.existsCorrectRoundChangeSet(config.getId(), consensusInstance, round);

            // Upon rule -> if received a valid set of (f + 1) broadcast ROUND-CHANGE with round rmin
            // TODO - check if rmin <= rj
            if(roundChangeQuorum.isPresent() && instance.getPreparedRound() < round) {
                instance.setLatestRoundChangeBroadcast(round);
                ConsensusMessage broadcastMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                        .setConsensusInstance(message.getConsensusInstance())
                        .setRound(message.getRound())
                        .setMessage(message.getMessage())
                        .setJustification(message.getJustification())
                        .build();
                // Broadcast with self signature and senderId
                this.link.broadcast(broadcastMessage);
            }
        }

        if (instance.getLatestRoundChange() >=  round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received ROUND-CHANGE quorum for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        // Verify if it has received Quorum, ROUND_CHANGE messages
        // if it has, JustifyRoundChange
        roundChangeQuorum = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round);

        List<ConsensusMessage> receivedJustification = message.getJustification();

        // Upon rule -> Check if it has received a round change quorum
        if (roundChangeQuorum.isPresent() &&
                instance.getPreparedRound() < round &&
                justifyRoundChange(config.getId(), consensusInstance, round, receivedJustification)) {

            System.out.println("\nROUND CHANGE QUORUM RECEIVED");

            instance.setLatestRoundChange(round);

            // Update the leader of the consensus
            // (remove the old leader and make the one with the id of the previous leader + 1 the new leader)
            Arrays.stream(nodesConfig).forEach(
                    processConfig -> { if (isLeader(processConfig.getId())) processConfig.setLeader(false); }
            );

            int currentLeaderId = Integer.parseInt(leaderConfig.getId());
            String newLeaderId;

            // TODO - if leader doesnt crash and round change still gets through, leader gets confused
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
                Block value = instance.getPreparedBlock();
                if (value == null) {
                    value = instance.getInputBlock();
                }
                // Start a new consensus by broadcasting a PRE-PREPARE message
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));

                // Create PrePrepare message to broadcast
                ConsensusMessage prePrepareMessage = this.createConsensusMessage(value, consensusInstance, instance.getCurrentRound());
                prePrepareMessage.setJustification(receivedJustification);

                this.link.broadcast(prePrepareMessage);
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
        String preparedValue = existingConsensus.getPreparedBlock() != null ? existingConsensus.getPreparedBlock().toJson() : "";
        int preparedRound = existingConsensus.getPreparedBlock() != null ? existingConsensus.getCurrentRound() : -1;

        // Increment the round in the instanceInfo of the node
        existingConsensus.incrementCurrentRound();
        this.instanceInfo.put(timerInstance, existingConsensus);

        // just to be clearer to read
        String value = existingConsensus.getInputBlock().toJson();
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
                prepareMessages.getPrepareMessages(config.getId(), consensusInstance.get(), existingConsensus.getPreparedRound());

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
