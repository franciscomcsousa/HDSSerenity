package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class PrePrepareMessage {
    
    // Block
    private String block;

    public PrePrepareMessage(String block) {
        this.block = block;
    }

    public String getBlock() {
        return block;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}   
