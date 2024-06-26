package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    /** Quorum size */
    private final int quorumSize;

    /** f + 1 quorum size */
    private final int existsCorrectSet;

    /** Instance -> Round -> Sender ID -> Consensus message */
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

    /**
     * Add a message to the bucket
     *
     * @param message message to add
     */
    public void addMessage(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    /**
     * Gives all prepare messages for a given instance and round
     *
     * @param instance instance
     * @param round round
     * @return Optional<List<ConsensusMessage>> - Optional list
     */
    public Optional<List<ConsensusMessage>> getPrepareMessages(int instance, int round) {
        if (bucket.get(instance) == null || bucket.get(instance).get(round) == null) {
            return Optional.empty();
        }

        return Optional.of(bucket.get(instance).get(round).values().stream().toList());
    }

    /**
     * Gives all commit messages for a given instance and round
     *
     * @param instance instance
     * @param round round
     * @return Optional<List<ConsensusMessage>> - Optional list
     */
    public Optional<List<ConsensusMessage>> getCommitMessages(int instance, int round) {
        if (bucket.get(instance) == null || bucket.get(instance).get(round) == null) {
            return Optional.empty();
        }

        return Optional.of(bucket.get(instance).get(round).values().stream().toList());
    }

    /**
     * Checks whether there is a valid Prepare quorum
     *
     * @param nodeId nodeId
     * @param instance instance
     * @param round round
     * @return Optional<String> - Empty if no quorum, otherwise value of the quorum
     */
    public Optional<String> hasValidPrepareQuorum(String nodeId, int instance, int round) {
        if (bucket.get(instance) == null || bucket.get(instance).get(round) == null)
            return Optional.empty();

        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();

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

    /**
     * Checks whether there is a valid Commit quorum
     *
     * @param instance instance
     * @param round round
     * @return Optional<String> - Empty if no quorum, otherwise value of the quorum
     */
    public Optional<String> hasValidCommitQuorum(int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
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

    /**
     * Returns only if there is an (f + 1) set with rj > ri for all Round Change messages
     *
     * @param instance instance
     * @param currentRound currentRound
     * @return Optional<RoundChangeMessage> - The message with the lowest prepared round of any Round Change message in the set
     */
    public Optional<RoundChangeMessage> hasCorrectRoundChangeInSet(int instance, int currentRound) {
        List<ConsensusMessage> set = new ArrayList<>();
        // Filters all the rounds that are greater than currentRound
        // Adds all Round Change messages that have a round greater than currentRound
        Stream<Integer> roundSet = bucket.get(instance).keySet().stream().filter(r -> r > currentRound);
        roundSet.forEach((integer -> {
            bucket.get(instance).get(integer).forEach(((s, consensusMessage) -> set.add(consensusMessage)));
        }));

        // Checks if the set size is greater than f+1
        if (set.size() < existsCorrectSet)
            return Optional.empty();

        // Returns the lowest prepared round of any Round Change message in the set
        return set.stream().min(
                Comparator.comparingInt(c -> c.deserializeRoundChangeMessage().getPreparedRound())
        ).map(ConsensusMessage::deserializeRoundChangeMessage);
    }

    /**
     * Returns only if there is a quorum of Round Change messages for the given round
     *
     * @param instance instance
     * @param round round
     * @return Optional<RoundChangeMessage> - The message with the highest prepared round of any Round Change message in the quorum
     */
    public Optional<RoundChangeMessage> hasValidRoundChangeQuorum(int instance, int round) {
        if (bucket.get(instance).get(round) == null || bucket.get(instance).get(round).size() < quorumSize)
            return Optional.empty();

        return bucket.get(instance).get(round).values().stream().max(
                Comparator.comparingInt(c -> c.deserializeRoundChangeMessage().getPreparedRound())
        ).map(ConsensusMessage::deserializeRoundChangeMessage);
    }

    /**
     * Gives the message with the highest prepared round
     * Should only be used when a quorum is already guaranteed
     *
     * @param instance instance
     * @param round round
     * @return Optional<RoundChangeMessage> - message with the highest prepared round
     */
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

    /**
     * Checks if all the messages in Qrc have not prepared round and value
     * J1 of Round Change justification
     *
     * @param instance instance
     * @param round round
     * @return boolean - whether there is a prepared justification
     */
    public boolean nonePreparedJustification(int instance, int round) {
        if (bucket.get(instance).get(round) == null)
            return false;
        return bucket.get(instance).get(round).values().stream().noneMatch(message -> {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            return (roundChangeMessage != null && !Objects.equals(roundChangeMessage.getPreparedValue(), ""));
        });
    }

    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        return bucket.get(instance).get(round);
    }
}