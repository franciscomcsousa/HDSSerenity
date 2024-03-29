package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class TransferMessage {

    private final String sender;
    private final String receiver;
    private final int amount;

    private final int nonce;


    public TransferMessage(String sender, String receiver, Integer amount, Integer nonce) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.nonce = nonce;
    }

    public String getSender() {
        return sender;
    }
    public String getReceiver() {
        return receiver;
    }

    public int getAmount() { return amount; }

    public int getNonce() { return nonce; }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
