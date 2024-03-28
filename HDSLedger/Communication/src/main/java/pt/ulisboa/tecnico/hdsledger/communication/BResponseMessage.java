package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
public class BResponseMessage {
    
    // Balance
    private int balance;

    public BResponseMessage(int balance) {
        this.balance = balance;
    }

    public int getBalance() {
        return balance;
    }
    
    public String toJson() {
        return new Gson().toJson(this);
    }
}
