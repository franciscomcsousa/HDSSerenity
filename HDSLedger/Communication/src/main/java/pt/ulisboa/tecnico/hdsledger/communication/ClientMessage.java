package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class ClientMessage extends Message {

    // Who sent the previous message
    private String replyTo;
    // Id of the previous message
    private int replyToMessageId;
    // Message (APPEND, RESPONSE)
    private String message;

    public ClientMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public RequestMessage deserializeRequestMessage() {
        return new Gson().fromJson(this.message, RequestMessage.class);
    }

    public ResponseMessage deserializeResponseMessage() {
        return new Gson().fromJson(this.message, ResponseMessage.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public int getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(int replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
