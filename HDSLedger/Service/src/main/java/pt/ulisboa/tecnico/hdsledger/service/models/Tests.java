package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Random;

import pt.ulisboa.tecnico.hdsledger.utilities.RSASignature;

public class Tests {
    
    public static Block createNewBlock(String nodeId) throws Exception{
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
}
