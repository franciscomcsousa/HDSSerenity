package pt.ulisboa.tecnico.hdsledger.communication;

public class ResponseMessage extends Message {
    
    // Consensus instance
    private int consensusInstance;

    // String value
    private String value;

    public ResponseMessage(String senderId, Type type, String value) {
        super(senderId, type);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }
}
