package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class TransferMessage {

    private final String sender;
    private final String receiver;
    private final int amount;


    public TransferMessage(String sender, String receiver, Integer amount) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
    }

    public String getSender() {
        return sender;
    }
    public String getReceiver() {
        return receiver;
    }

    public Integer getAmount() { return amount; }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
