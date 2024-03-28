package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Nonce -> TransferResponseMessages
    // TODO - may be necessary to see if there are some different responses
    private Map<Integer, List<TResponseMessage>> transferResponses = new ConcurrentHashMap<>();

    public ClientLibrary(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) {
        this.clientConfig = clientConfig;
        this.nodeConfigs = nodeConfigs;
        this.linkToNodes = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientMessage.class);

        int f = Math.floorDiv(nodeConfigs.length - 1, 3);
        this.quorumSize = Math.floorDiv(nodeConfigs.length + f, 2) + 1;
        this.smallQuorumSize = f + 1;
    }


    public void transfer(String nodeId, String destination, Integer amount) {

        // Create a message and broadcast it to the nodes
        ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.TRANSFER);
        TransferMessage transferMessage = new TransferMessage(nodeId, destination, amount);
        clientMessage.setMessage(transferMessage.toJson());

        linkToNodes.broadcast(clientMessage);
    }



        // Checks user balance
    // TODO
    public void check_balance(){

    }

    public void uponTransferResponse(ClientMessage message) {
        TResponseMessage transferResponse = message.deserializeTResponseMessage();
        Transaction transaction = Transaction.fromJson(transferResponse.getTransaction());
        int nonce = transaction.getNonce();

        // adds response to the list, and checks how many it has
        transferResponses.computeIfAbsent(nonce, key -> new ArrayList<>())
                .add(transferResponse);

        // Prints when its exactly f + 1
        if (transferResponses.get(nonce).size() == smallQuorumSize) {
            System.out.println(MessageFormat.format(
                    "{0} - Transfer finished, from node {1} in position {2}. Transfer with amount {3} to client {4}",
                    clientConfig.getId(), message.getSenderId(), message.getPosition(),
                    transaction.getAmount(),
                    transaction.getReceiver())
            );

            System.out.println();
            System.out.print(">> ");
        }
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
                            case TRANSFER_RESPONSE:
                                uponTransferResponse((ClientMessage) message);
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
