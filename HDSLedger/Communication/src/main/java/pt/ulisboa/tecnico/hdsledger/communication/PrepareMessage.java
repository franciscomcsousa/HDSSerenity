package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class PrepareMessage {
    
    // Block
    private String block;

    public PrepareMessage(String block) {
        this.block = block;
    }

    public String getBlock() {
        return block;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}   
