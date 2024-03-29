package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.LinkedList;
import java.util.List;
import com.google.gson.Gson;

public class Block {

    // TODO - add special transaction, fixed value sent to block creator
    private List<Transaction> transactions = new LinkedList<Transaction>();

    private int maxBlockSize = 2;

    // Node who created
    private String authorId;

    public Block() {

    }

    public int getMaxBlockSize() {
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
        return signable;
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
