package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class ClientMessage extends Message {

    // Who sent the previous message
    private String replyTo;
    // Id of the previous message
    private int replyToMessageId;
    // Message (TRANSFER, BALANCE, RESPONSE)
    private String message;

    public ClientMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public TransferMessage deserializeTransferMessage() {
        return new Gson().fromJson(this.message, TransferMessage.class);
    }

    public BalanceMessage deserializeBalanceMessage() {
        return new Gson().fromJson(this.message, BalanceMessage.class);
    }

    public ResponseMessage deserializeResponseMessage() {
        return new Gson().fromJson(this.message, ResponseMessage.class);
    }

    public TResponseMessage deserializeTResponseMessage() {
        return new Gson().fromJson(this.message, TResponseMessage.class);
    }

    public BResponseMessage deserializeBResponseMessage() {
        return new Gson().fromJson(this.message, BResponseMessage.class);
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

    @Override
    public String getSignable(){
        return super.getSenderId() + super.getMessageId() + super.getType().toString()
                + getReplyTo() + getReplyToMessageId() + getMessage();
    }
}