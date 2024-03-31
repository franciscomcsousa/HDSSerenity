package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSASignature;

public class Tests {

    private static Block createNewBlock(String nodeId) throws Exception{
        Block block = new Block();
        Random random = new Random();

        // Add to block different made up transactions
        for (int i = 0; i < block.getMaxBlockSize(); i++) {
            int randomInt = random.nextInt();
            String signable = "20" + "21" + "100" + randomInt;
            Transaction transaction = new Transaction(
                    "20",
                    "21",
                    100,
                    randomInt,
                    RSASignature.sign(signable, nodeId));
            block.addTransaction(transaction);
        }

        block.setAuthorId(nodeId);
        String blockSignable = block.getSignable();
        block.setSignature(RSASignature.sign(blockSignable, nodeId));

        return block;
    }

    private static Block createNewBlockWithNonce(String nodeId, int nonce) throws Exception{
        Block block = new Block();

        // Add to block different made up transactions
        for (int i = 0; i < block.getMaxBlockSize(); i++) {
            String signable = "20" + "21" + "100" + nonce;
            Transaction transaction = new Transaction(
                    "20",
                    "21",
                    100,
                    nonce,
                    RSASignature.sign(signable, nodeId));
            block.addTransaction(transaction);
        }

        block.setAuthorId(nodeId);
        String blockSignable = block.getSignable();
        block.setSignature(RSASignature.sign(blockSignable, nodeId));

        return block;
    }

    /**
     * Attacker model:
     * Has full knowledge of the code
     * Doesn't have access to other's private keys
     * Here attacker is the leader and tries to insert made up transactions on a block
     *
     * @param behavior type of behaviour
     * @param nodeId attacking node (only has access to its private key)
     * @return Block - the byzantine block
     * @throws Exception exception
     */
    public static Optional<Block> differentValue(ProcessConfig.Behavior behavior, String nodeId) throws Exception {
        if (behavior == ProcessConfig.Behavior.DIFF_VALUE) {
            return Optional.of(Tests.createNewBlock(nodeId));
        }
        return Optional.empty();
    }

    /**
     * Attacker model:
     * Has full knowledge of the code
     * Doesn't have access to other's private keys
     * Here attacker is the leader and tries to insert made up transactions on a block
     *
     * @param behavior type of behaviour
     * @param nodeId attacking node (only has access to its private key)
     * @return Block - the byzantine block
     * @throws Exception exception
     */
    public static Optional<Block> differentPrepareValue(ProcessConfig.Behavior behavior, String nodeId) throws Exception {
        if (behavior == ProcessConfig.Behavior.PREPARE_VALUE) {
            return Optional.of(Tests.createNewBlock(nodeId));
        }
        return Optional.empty();
    }

    /**
     * Attacker model:
     * Has full knowledge of the code
     * Doesn't have access to other's private keys
     * Here a node creates a commit message with a different block
     *
     * @param behavior type of behaviour
     * @param nodeId attacking node (only has access to its private key)
     * @return Block - the byzantine block
     * @throws Exception exception
     */
    public static Optional<Block> differentCommitValue(ProcessConfig.Behavior behavior, String nodeId) throws Exception {
        if (behavior == ProcessConfig.Behavior.COMMIT_VALUE) {
            return Optional.of(Tests.createNewBlock(nodeId));
        }
        return Optional.empty();
    }

    /**
     *  This tests the behaviour when no one commits for a round, i.e.
     *  tests the Round Change with a Prepared value, which in turn tests
     *  both the Round Change and Pre-Prepare justification logic
     *
     * @param behavior type of behaviour
     * @param round id of the current round
     * @return boolean - whether to perform the test
     */
    public static boolean noCommit(ProcessConfig.Behavior behavior, int round) {
        if (behavior == ProcessConfig.Behavior.NO_COMMIT) {
            // Here all nodes return without sending commit messages only for the first round
            if (round == 1)
                return true;
            else
                return false;
        }
        return false;
    }

    /**
     *  This tests the behaviour when a node sends a replay attack
     *
     * @param behavior type of behaviour
     * @param nodeId attacking node (only has access to its private key)
     * @param instance id of the current instance
     * @param nonces list of nonces
     * @return Block - the byzantine block
     * @throws Exception exception
     */
    public static Optional<Block> nodeReplayAttack(ProcessConfig.Behavior behavior, String nodeId, int instance, List<Integer> nonces) throws Exception{
        if (behavior == ProcessConfig.Behavior.NODE_REPLAY_ATTACK) {
            // Here the attacker sends a replay attack on the second instance
            if (instance == 2 && nonces.size() > 0) {
                return Optional.of(Tests.createNewBlockWithNonce(nodeId, nonces.get(0)));
            }
        }
        return Optional.empty();
    }

    /**
     *  This tests the behaviour when a client sends a replay attack
     *
     * @param behavior type of behaviour
     * @return boolean - whether to perform the test
     */
    public static boolean clientReplayAttack(ProcessConfig.Behavior behavior) {
        if (behavior == ProcessConfig.Behavior.CLIENT_REPLAY_ATTACK) {
            return true;
        }
        return false;
    }

    /**
     *  This tests the behaviour when a leader starts a consensus with a big instance
     *
     * @param behavior type of behaviour
     * @param instance id of the current instance
     * @return boolean - whether to perform the test
     */
    public static boolean bigInstance(ProcessConfig.Behavior behavior, int instance) {
        if (behavior == ProcessConfig.Behavior.BIG_INSTANCE) {
            // Here the leader starts a consensus with a big instance on the second instance
            if (instance == 2)
                return true;
            else
                return false;
        }
        return false;
    }

    /**
     *  This tests the behaviour when a leader ignores a client's requests
     *
     * @param behavior type of behaviour
     * @return boolean - whether to perform the test
     */
    public static boolean ignoreClient(ProcessConfig.Behavior behavior) {
        if (behavior == ProcessConfig.Behavior.IGNORE_CLIENT) {
            return true;
        }
        return false;
    }

    /**
     *  This tests the behaviour when a node doesn't receive messages from other nodes, triggers a timerExpiry and receives a quorum of commits afterwards
     *
     * @param behavior type of behaviour
     * @param round id of the current round
     * @return boolean - whether to perform the test
     */
    public static boolean commitQuorum(ProcessConfig.Behavior behavior, int round) {
        if (behavior == ProcessConfig.Behavior.COMMIT_QUORUM) {
            // Here the node doesn't receive messages from other nodes, triggers a timerExpiry and receives a quorum of commits afterwards
            if (round == 1)
                return true;
            else
                return false;
        }
        return false;
    }
}
