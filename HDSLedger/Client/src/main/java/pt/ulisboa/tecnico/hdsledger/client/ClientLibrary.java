package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.io.IOException;
import java.text.MessageFormat;

//
// Client library used as a bridge between the client and the nodes in the blockchain
//
public class ClientLibrary {
    
    private final ProcessConfig clientConfig;
    private final ProcessConfig[] nodeConfigs;
    private final Link linkToNodes;

    public ClientLibrary(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) {
        this.clientConfig = clientConfig;
        this.nodeConfigs = nodeConfigs;
        this.linkToNodes = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientMessage.class);
    }


    // Append a value to the blockchain
    public void append(String value) {

        // TODO - is it too much to create a builder? for now seems overkill
        ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.APPEND);
        clientMessage.setMessage(value);
        //ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
        linkToNodes.broadcast(clientMessage);
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
                                ClientMessage clientMessage = (ClientMessage) message;
                                System.out.println(MessageFormat.format("{0} - Commit finished from node {1} for value \"{2}\" in position {3}", 
                                    clientConfig.getId(), clientMessage.getSenderId(), clientMessage.getMessage(), clientMessage.getPosition()));
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
