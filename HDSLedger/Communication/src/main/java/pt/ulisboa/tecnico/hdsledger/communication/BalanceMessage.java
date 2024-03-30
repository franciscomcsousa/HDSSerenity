package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class BalanceMessage {
    
    private final String nodeId;

    public BalanceMessage(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String getSignable(){
        return getNodeId();
    }
}
