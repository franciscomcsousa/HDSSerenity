package pt.ulisboa.tecnico.hdsledger.service.models;

import java.security.SecureRandom;
import java.util.stream.LongStream;

public class Transaction {
    private String sender;
    private String receiver;
    private int value;
    private LongStream nounce;
    private byte[] signature;

    private SecureRandom randomGen = new SecureRandom();

    public Transaction(String sender, String receiver, int value){
        this.sender = sender;
        this.receiver = receiver;
        this.value = value;
        this.nounce = randomGen.longs();
    }

    public float getValue() {
        return value;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getSender() {
        return sender;
    }

    public LongStream getNounce() {
        return nounce;
    }

    public byte[] getSignature() {
        return signature;
    }

    public String getSignable(){
        return getSender() + getReceiver() + getValue() + getNounce();
    }
}
