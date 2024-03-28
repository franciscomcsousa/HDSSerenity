package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
public class TResponseMessage {

    // Transaction
    private String transaction;

    // Position in the blockchain
    private int position;

    public TResponseMessage(String transaction, int position) {
        this.transaction = transaction;
        this.position = position;
    }

    public String getTransaction() {
        return transaction;
    }

    public void setTransaction(String newTransaction) {
        this.transaction = newTransaction;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

}
