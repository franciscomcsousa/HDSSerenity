package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
public class BResponseMessage {
    
    // Balance
    private Double balance;

    public BResponseMessage(Double balance) {
        this.balance = balance;
    }

    public Double getBalance() {
        return balance;
    }
    
    public String toJson() {
        return new Gson().toJson(this);
    }
}
