package pt.ulisboa.tecnico.hdsledger.communication;

public class ResponseMessage extends Message {
    
    // Consensus instance
    private int consensusInstance;

    // String value
    private String value;

    // Position in the blockchain
    private int position;

    public ResponseMessage(String senderId, Type type, String value, int position) {
        super(senderId, type);
        this.value = value;
        this.position = position;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }
}
