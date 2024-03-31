package pt.ulisboa.tecnico.hdsledger.communication;

public class Data {

    // Message in the data sent
    private Message message;

    //

    private final byte[] signature;

    public Data(Message message, byte[] signature) {
        this.message = message;
        this.signature = signature;
    }

    public Message getMessage() {
        return message;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setMessage(Message message) { this.message = message; }
}
