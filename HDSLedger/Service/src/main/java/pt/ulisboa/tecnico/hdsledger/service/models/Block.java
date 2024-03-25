package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;

public class Block {

    private List<ClientMessage> messages = new ArrayList<>();
    private int blockSize = 2;
    // block hash
    private String hash;

    public Block() {

    }

    public int getBlockSize() {
        return blockSize;
    }

    public List<ClientMessage> getRequests() {
        return messages;
    }

    public void addRequest(ClientMessage message){
        this.messages.add(message);
    }
    
    public String getSignable(){
        String signable = "";
        for (ClientMessage message:
             getRequests()) {
            signable = signable.concat(message.getSignable());
        }
        return signable;
    }

    public static Block fromJson(String json){
        return new Gson().fromJson(json, Block.class);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return this.toJson();
    }
}
