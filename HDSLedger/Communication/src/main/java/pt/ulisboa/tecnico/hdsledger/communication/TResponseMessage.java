package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
public class TResponseMessage {

    // Transaction
    private String transaction;

    // Position in the blockchain
    private int position;

    // Message Status
    private Status status;

    // Status, can be successful or fail
    public enum Status {
        SUCCESS, FAILED_BALANCE, FAILED_RECEIVER, FAILED_SENDER;
    }

    public TResponseMessage(String transaction, Status status) {
        this.transaction = transaction;
        this.status = status;
    }

    public String getTransaction() {
        return transaction;
    }

    public Status getStatus() { return status; }

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
