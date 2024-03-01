package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RequestMessage extends Message {

    // Consensus instance
    private int consensusInstance;

    // String value
    private String message;

    public RequestMessage(String senderId, Type type, String message) {
        super(senderId, type);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }
}
