package pt.ulisboa.tecnico.hdsledger.service.models;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.utilities.Colors;

public class Transaction {
    private final String sender;
    private final String receiver;
    private final double amount;
    private final int nonce;
    private final byte[] signature;

    public Transaction(String sender, String receiver, double amount, Integer nonce, byte[] signature){
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.nonce = nonce;
        this.signature = signature;
    }

    public double getAmount() {
        return amount;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getSender() {
        return sender;
    }

    public int getNonce() {
        return nonce;
    }

    public byte[] getSignature() {
        return signature;
    }

    public String getSignable(){
        return getSender() + getReceiver() + getAmount() + getNonce();
    }
    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Transaction fromJson(String json){
        return new Gson().fromJson(json, Transaction.class);
    }


}
