package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.utilities.RSASignature;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class TransferMessage {

    private final String sender;
    private final String receiver;
    private final double amount;
    private final int nonce;
    private byte[] signature;

    public TransferMessage(String sender, String receiver, Double amount) throws Exception {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.nonce = createNonce();
        // Each client only has access to their private key
        this.signature = RSASignature.sign(this.getSignable(), sender);
    }

    private int createNonce() {
        try {
            return SecureRandom.getInstance("SHA1PRNG").nextInt();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public String getSender() {
        return sender;
    }
    public String getReceiver() {
        return receiver;
    }

    public double getAmount() { return amount; }

    public int getNonce() { return nonce; }

    public byte[] getSignature() {
        return signature;
    }

    public String getSignable(){
        return getSender() + getReceiver() + getAmount() + getNonce();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
