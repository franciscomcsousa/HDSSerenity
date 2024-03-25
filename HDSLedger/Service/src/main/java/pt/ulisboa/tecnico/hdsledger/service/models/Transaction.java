package pt.ulisboa.tecnico.hdsledger.service.models;

public class Transaction {
    private String sender;
    private String receiver;
    private int value;
    private int nounce;
    private byte[] signature;

    public Transaction(String sender, String receiver, int value){
        this.sender = sender;
        this.receiver = receiver;
        this.value = value;
        this.nounce = 0;
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

    public int getNounce() {
        return nounce;
    }

    public byte[] getSignature() {
        return signature;
    }

    public String getSignable(){
        return getSender() + getReceiver() + getValue() + getNounce();
    }
}
