package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;

public class Requests {
    
    private final Queue<ClientMessage> requests = new LinkedList<>();

    private final int blockSize = 2;

    public Requests() {
    }

    public void addRequest(ClientMessage request) {
        synchronized (requests) {
            requests.add(request);
        }
    }

    public void removeRequest(ClientMessage request) {
        synchronized (requests) {
            requests.remove(request);
        }
    }

    public boolean isEnoughRequests() {
        synchronized (requests) {
            return requests.size() >= blockSize;
        }
    }

    public Block createBlock() {
        synchronized (requests) {
            Block block = new Block();
            for (int i = 0; i < blockSize; i++) {
                ClientMessage request = requests.poll();
                block.addRequest(request);
            }
            return block;
        }
    }
}
