package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;

public class Block {

    private List<Transaction> transactions;

    private int maxBlockSize = 2;

    // TODO
    // Node who created
    private int nodeId;

    public Block() {

    }

    public int getMaxBlockSize() {
        return maxBlockSize;
    }

    public int getBlockSize() { return transactions.size(); }

    public List<Transaction> getTransactions() { return transactions; }

    public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }


    // TODO - change to only sign the block stuff
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
