package pt.ulisboa.tecnico.hdsledger.client;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

// TODO remove useless imports after finishing the implementation

//
// Client library used as a bridge between the client and the nodes in the blockchain
//
public class ClientService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());
    
    private final ProcessConfig clientConfig;
    private final ProcessConfig[] nodeConfigs;
    private final Link linkToNodes;

    public ClientService(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) {
        this.clientConfig = clientConfig;
        this.nodeConfigs = nodeConfigs;
        this.linkToNodes = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, RequestMessage.class);
    }


    // Append a value to the blockchain
    public void append(String value) {

        RequestMessage requestMessage = new RequestMessage(clientConfig.getId(), Message.Type.APPEND, value);
        ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
        linkToNodes.send(leaderConfig.getId(), requestMessage);
    }

    // Listen for replies from the nodes
    public void listen() {
        try{
            new Thread(() -> {
                try{
                    while(true){

                        Data data = linkToNodes.receive();
                        Message message = data.getMessage();

                        // Case for each type of message
                        switch(message.getType()){
                            case ACK:
                                System.out.println(MessageFormat.format("{0} - Received ACK message from {1}", clientConfig.getId(), message.getSenderId()));
                                System.out.println();
                                System.out.print(">> ");
                                break;
                            default:
                                System.out.println("Unknown message type received");
                                System.out.println();
                                System.out.print(">> ");
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
