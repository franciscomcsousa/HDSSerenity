package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    // Quorum size
    private final int quorumSize;

    // f + 1 quorum size
    private final int existsCorrectSet;

    // Instance -> Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    public void printBucket() {
        for (int instance : this.bucket.keySet()) {
            System.out.println("Instance: " + instance);
            for (int round : bucket.get(instance).keySet()) {
                System.out.println("    Round: " + round);
                for (String senderId : bucket.get(instance).get(round).keySet()) {
                    System.out.println("        Sender ID: " + senderId);
                    System.out.println("        Message: " + bucket.get(instance).get(round).get(senderId));
                }
            }
        }
    }

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
        existsCorrectSet = f + 1;
    }

    /*
     * Add a message to the bucket
     *
     * @param consensusInstance
     *
     * @param message
     */
    public void addMessage(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    public Optional<List<ConsensusMessage>> getPrepareMessages(String nodeId, int instance, int round) {
        if (bucket.get(instance) == null || bucket.get(instance).get(round) == null) {
            return Optional.empty();
        }

        return Optional.of(bucket.get(instance).get(round).values().stream().toList());
    }

    public Optional<String> hasValidPrepareQuorum(String nodeId, int instance, int round) {
        if (bucket.get(instance) == null || bucket.get(instance).get(round) == null)
            return Optional.empty();

        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            //Block block = Block.fromJson(prepareMessage.getBlock());
            // TODO - please do not let this be a key

            frequency.put(prepareMessage.getBlock(), frequency.getOrDefault(prepareMessage.getBlock(), 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<String> hasValidCommitQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            //Block block = Block.fromJson(commitMessage.getBlock());
            frequency.put(commitMessage.getBlock(), frequency.getOrDefault(commitMessage.getBlock(), 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    // TODO - maybe do this in the same function instead of two?
    public Optional<String> existsCorrectRoundChangeSet(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            String value = roundChangeMessage.getPreparedValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= existsCorrectSet;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<String> hasValidRoundChangeQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            String value = roundChangeMessage.getPreparedValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    // Should only be applied when a quorum is guaranteed
    public Optional<RoundChangeMessage> highestPrepared(int instance, int round){
        RoundChangeMessage highestRoundChangeMessage = null;
        int highestPreparedRound = -1;
        for (ConsensusMessage message : bucket.get(instance).get(round).values()) {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            if (roundChangeMessage.getPreparedRound() > highestPreparedRound)
                highestRoundChangeMessage = roundChangeMessage;
        }
        return Optional.ofNullable(highestRoundChangeMessage);
    }

    /*
     * Checks if all of the messages in Qrc have no prepared round and value
     * J1 of Round Change justification
     */
    public boolean nonePreparedJustification(int instance, int round) {
        return bucket.get(instance).get(round).values().stream().noneMatch(message -> {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            return (roundChangeMessage != null && !Objects.equals(roundChangeMessage.getPreparedValue(), ""));
        });
    }

    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        return bucket.get(instance).get(round);
    }
}