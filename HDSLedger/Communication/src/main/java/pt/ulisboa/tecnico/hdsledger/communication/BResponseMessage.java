package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
public class BResponseMessage {
    
    // Node id
    private String nodeId;

    // Balance
    private Double balance;

    // Message Status
    private Status status;

    // Status, can be successful or fail
    public enum Status {
        SUCCESS,
        FAILED_ID;
    }

    public BResponseMessage(String nodeId, Double balance, Status status) {
        this.nodeId = nodeId;
        this.balance = balance;
        this.status = status;
    }

    public String getNodeId() {
        return nodeId;
    }

    public Double getBalance() {
        return balance;
    }

    public Status getStatus() { return status; }
    
    public String toJson() {
        return new Gson().toJson(this);
    }
}
