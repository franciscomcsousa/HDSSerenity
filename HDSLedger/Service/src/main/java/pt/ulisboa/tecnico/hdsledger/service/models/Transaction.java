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

    public Transaction(String sender, String receiver, Integer amount, Integer nonce){
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.nonce = nonce;
    }

    public int getAmount() {
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
