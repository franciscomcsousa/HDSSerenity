package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.io.IOException;
import java.text.MessageFormat;

import com.google.gson.Gson;

//
// Client library used as a bridge between the client and the nodes in the blockchain
//
public class ClientLibrary {
    
    private final ProcessConfig clientConfig;
    private final ProcessConfig[] nodeConfigs;
    private final Link linkToNodes;
    private final int quorumSize;
    private final int smallQuorumSize;
    private int receivedResponses = 0;

    public ClientLibrary(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) {
        this.clientConfig = clientConfig;
        this.nodeConfigs = nodeConfigs;
        this.linkToNodes = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientMessage.class);

        int f = Math.floorDiv(nodeConfigs.length - 1, 3);
        quorumSize = Math.floorDiv(nodeConfigs.length + f, 2) + 1;
        smallQuorumSize = f + 1;
    }


    // Append a value to the blockchain
    public void append(String value) {

        // Reset the number of received responses
        receivedResponses = 0;

        // Create a message and broadcast it to the nodes
        //ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.APPEND);
        //clientMessage.setMessage(value);
        //linkToNodes.broadcast(clientMessage);

        // Client waits for a smallQuorum (f + 1) of RESPONSE messages using the message bucket
        System.out.println(MessageFormat.format("{0} - Waiting for a quorum of responses for value \"{1}\"", clientConfig.getId(), value));
        while (true) {
            // Sleep for a while to avoid busy waiting
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (receivedResponses >= smallQuorumSize) {
                System.out.println(MessageFormat.format("{0} - Received a quorum of responses for value \"{1}\"", clientConfig.getId(), value));
                System.out.println();
                System.out.print(">> ");
                receivedResponses = 0;
                break;
            }
        }
    }

    public void transfer(String nodeId, String destination, Integer amount) {
        // Reset the number of received responses
        receivedResponses = 0;

        // Create a message and broadcast it to the nodes
        ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.TRANSFER);
        TransferMessage transferMessage = new TransferMessage(nodeId, destination, amount);
        clientMessage.setMessage(transferMessage.toJson());

        linkToNodes.broadcast(clientMessage);

        // Client waits for a smallQuorum (f + 1) of RESPONSE messages using the message bucket
        System.out.println(MessageFormat.format("{0} - Waiting for a quorum of responses for amount \"{1}\"", clientConfig.getId(), amount));
        while (true) {
            // Sleep for a while to avoid busy waiting
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (receivedResponses >= smallQuorumSize) {
                System.out.println(MessageFormat.format("{0} - Received a quorum of responses for amount \"{1}\"", clientConfig.getId(), amount));
                System.out.println();
                System.out.print(">> ");
                receivedResponses = 0;
                break;
            }
        }
    }



        // Checks user balance
    // TODO
    public void check_balance(){

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
                                TransferMessage transferMessage = new Gson().fromJson(clientMessage.getMessage(), TransferMessage.class);
                                if (receivedResponses < smallQuorumSize) {
                                    receivedResponses++;
                                }
                                System.out.println(MessageFormat.format(
                                    "{0} - Commit finished from node {1} in position {2} for transfer of value {3} to node {4}", 
                                    clientConfig.getId(), clientMessage.getSenderId(), clientMessage.getPosition(),
                                    transferMessage.getAmount(),
                                    transferMessage.getReceiver()));
                                System.out.println();
                                System.out.print(">> ");
                                break;
                            case IGNORE, INVALID:
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
