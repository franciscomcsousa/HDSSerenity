package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class Message implements Serializable {

    // Sender identifier
    private String senderId;
    // Message identifier
    private int messageId;
    // Message type
    private Type type;

    // Signature
    private byte[] signature;

    public enum Type {
        // CONSENSUS
        PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE, ACK, IGNORE, INVALID,
        // CLIENT
        APPEND, RESPONSE;
    }

    public Message(String senderId, Type type) {
        this.senderId = senderId;
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    // Probably a good idea to have messages that extend from this to override this method
    public String getSignable(){
        return senderId + Integer.toString(messageId) + type.toString();
    }
}
