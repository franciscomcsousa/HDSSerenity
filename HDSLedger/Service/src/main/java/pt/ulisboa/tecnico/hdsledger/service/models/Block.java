package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;

public class Block {

    private List<Transaction> transactions = new ArrayList<>();
    private int blockSize = 2;
    private byte[] signature;

    public Block() {

    }

    public Block(int blockSize){
        this.blockSize = blockSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public byte[] getSignature() {
        return signature;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public boolean isBlockFull(){
        return transactions.size() >= blockSize;
    }

    public boolean addTransaction(Transaction transaction){
        if (!this.isBlockFull()){
            this.transactions.add(transaction);
            return true;
        }
        return false;
    }
    
    public String getSignable(){
        String signable = "";
        for (Transaction transaction:
             getTransactions()) {
            signable = signable.concat(transaction.getSignable());
        }
        return signable;
    }

}
