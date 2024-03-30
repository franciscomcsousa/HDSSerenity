package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.LinkedList;
import java.util.List;
import com.google.gson.Gson;

public class Block {

    // TODO - add special transaction, fixed value sent to block creator
    private List<Transaction> transactions = new LinkedList<Transaction>();

    private static int maxBlockSize = 2;

    // Node who created
    private String authorId;

    private byte[] signature;

    /** Empty Constructor mainly used for attack tests */
    public Block() {

    }

    public Block(String authorId, List<Transaction> transactions) {
        this.authorId = authorId;
        this.transactions = transactions;
    }

    public static int getMaxBlockSize() {
        return maxBlockSize;
    }

    public int getBlockSize() { return transactions.size(); }

    public List<Transaction> getTransactions() { return transactions; }

    public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }

    public void addTransaction(Transaction transaction) { this.transactions.add(transaction); }

    public String getAuthorId() { return authorId; }

    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getSignable(){
        String signable = "";
        for (Transaction transaction : getTransactions()) {
            signable = signable.concat(transaction.getSignable());
        }
        signable = signable + maxBlockSize + authorId;
        return signable;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public static Block fromJson(String json){
        return new Gson().fromJson(json, Block.class);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return this.toJson();
    }
}
