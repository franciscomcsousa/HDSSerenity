package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private Map<Integer, List<TResponseMessage>> transferResponses = new ConcurrentHashMap<>();

    // Received balance responses
    private int balanceResponses = 0;

    public ClientLibrary(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) {
        this.clientConfig = clientConfig;
        this.nodeConfigs = nodeConfigs;
        this.linkToNodes = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientMessage.class);

        int f = Math.floorDiv(nodeConfigs.length - 1, 3);
        this.quorumSize = Math.floorDiv(nodeConfigs.length + f, 2) + 1;
        this.smallQuorumSize = f + 1;
    }

    public int createNonce() {
        try {
            return SecureRandom.getInstance("SHA1PRNG").nextInt();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    // Transfer amount from the client to the destination
    public void transfer(String nodeId, String destination, Integer amount) {
        // Check if amount is positive
        if (amount <= 0) {
            System.out.println("Amount must be positive");
            System.out.println();
            System.out.print(">> ");
            return;
        }

        // Create a message and broadcast it to the nodes
        ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.TRANSFER);
        TransferMessage transferMessage = new TransferMessage(nodeId, destination, amount, createNonce());
        clientMessage.setMessage(transferMessage.toJson());

        linkToNodes.broadcast(clientMessage);
    }

    // Checks user balance
    public void check_balance(){
        // Reset the balance responses
        balanceResponses = 0;
        
        // Create a message and broadcast it to the nodes
        ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.BALANCE);
        linkToNodes.broadcast(clientMessage);
    }

    public void uponTransferResponse(ClientMessage message) {
        TResponseMessage transferResponse = message.deserializeTResponseMessage();
        Transaction transaction = Transaction.fromJson(transferResponse.getTransaction());
        int nonce = transaction.getNonce();

        // If transfer fails due to not enough balance
        if (transferResponse.getStatus() == TResponseMessage.Status.FAILED_BALANCE) {
            System.out.println(MessageFormat.format(
                    "{0} - Transfer Failed, from node {1}. Not enough balance to transfer amount {2} to client {3}",
                    clientConfig.getId(), message.getSenderId(),
                    transaction.getAmount(), transaction.getReceiver()));
            System.out.println();
            System.out.print(">> ");
            return;
        }
        // If transfer fails due to receiver not existing
        if (transferResponse.getStatus() == TResponseMessage.Status.FAILED_RECEIVER) {
            System.out.println(MessageFormat.format(
                    "{0} - Transfer Failed, from node {1}. Client {2} does not exist",
                    clientConfig.getId(), message.getSenderId(),
                    transaction.getReceiver()));
            System.out.println();
            System.out.print(">> ");
            return;
        }
        // If transfer fails due to sender being the same as the receiver
        if (transferResponse.getStatus() == TResponseMessage.Status.FAILED_SENDER) {
            System.out.println(MessageFormat.format(
                    "{0} - Transfer Failed, from node {1}. Sender and receiver are the same",
                    clientConfig.getId(), message.getSenderId()));
            System.out.println();
            System.out.print(">> ");
            return;
        }

        // Adds response to the list, and checks how many it has
        transferResponses.computeIfAbsent(nonce, key -> new ArrayList<>())
                .add(transferResponse);

        // Prints when its exactly f+1
        if (transferResponses.get(nonce).size() == smallQuorumSize) {
            System.out.println(MessageFormat.format(
                    "{0} - Transfer Completed from f+1 nodes in position {1}. Transfer amount: {2}. Transfer recipient: {3}",
                    clientConfig.getId(), transferResponse.getPosition(),
                    transaction.getAmount(), transaction.getReceiver())
            );
            System.out.println();
            System.out.print(">> ");
        }
    }

    public void uponBalanceResponse(ClientMessage message) {
        BResponseMessage balanceResponse = message.deserializeBResponseMessage();
        
        // Adds response
        balanceResponses++;

        // Prints when its exactly f+1
        if (balanceResponses == smallQuorumSize) {
            System.out.println(MessageFormat.format(
                    "{0} - Balance: {1}",
                    clientConfig.getId(), balanceResponse.getBalance())
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
                            case BALANCE_RESPONSE:
                                uponBalanceResponse((ClientMessage) message);
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
