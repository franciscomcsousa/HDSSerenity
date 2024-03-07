package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

//
// Client library used as a bridge between the client and the nodes in the blockchain
//
public class ClientService {
    
    private final ProcessConfig clientConfig;
    private final ProcessConfig[] nodeConfigs;
    private final Link linkToNodes;

    public ClientService(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) {
        this.clientConfig = clientConfig;
        this.nodeConfigs = nodeConfigs;
        this.linkToNodes = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ResponseMessage.class);
    }


    // Append a value to the blockchain
    public void append(String value) {

        RequestMessage requestMessage = new RequestMessage(clientConfig.getId(), Message.Type.APPEND, value);
        //ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
        linkToNodes.broadcast(requestMessage);
    }

    // Listen for replies from the nodes
    public void listen() {
        try{
            new Thread(() -> {
                try{
                    while(true){

                        Message message = linkToNodes.receive();

                        // Case for each type of message
                        switch(message.getType()){
                            case ACK:
                                System.out.println(MessageFormat.format("{0} - Received ACK message from {1}", clientConfig.getId(), message.getSenderId()));
                                System.out.println();
                                System.out.print(">> ");
                                break;
                            case RESPONSE:
                                ResponseMessage responseMessage = (ResponseMessage) message;
                                System.out.println(MessageFormat.format("{0} - Commit finished from node {1} for value \"{2}\" in position {3}", 
                                    clientConfig.getId(), responseMessage.getSenderId(), responseMessage.getValue(), responseMessage.getPosition()));
                                System.out.println();
                                System.out.print(">> ");
                                break;
                            case IGNORE:
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
