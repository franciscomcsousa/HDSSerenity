package pt.ulisboa.tecnico.hdsledger.service.models;

import com.google.gson.Gson;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Transaction {
    private String sender;
    private String receiver;
    private int amount;
    private int nonce;
    private byte[] signature;

    public Transaction(String sender, String receiver, int amount){
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        int newNonce;
        try {
            newNonce = SecureRandom.getInstance("SHA1PRNG").nextInt();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        this.nonce = newNonce;
    }

    public float getAmount() {
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
