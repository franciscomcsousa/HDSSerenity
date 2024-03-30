package pt.ulisboa.tecnico.hdsledger.service.models;

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

}
