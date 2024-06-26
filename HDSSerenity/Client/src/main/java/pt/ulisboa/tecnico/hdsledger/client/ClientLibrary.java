package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import java.io.IOException;
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

    // Transfer amount from the client to the destination
    public void transfer(String nodeId, String destination, Double amount) throws Exception {
        // Check if amount is positive
        if (amount <= 0) {
            System.out.println("Amount must be positive");
            System.out.println();
            System.out.print(">> ");
            return;
        }

        // Create a message and broadcast it to the nodes
        ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.TRANSFER);
        TransferMessage transferMessage = new TransferMessage(nodeId, destination, amount);
        clientMessage.setMessage(transferMessage.toJson());

        linkToNodes.broadcast(clientMessage);
    }

    // Checks user balance
    public void check_balance(String nodeId){
        // Reset the balance responses
        balanceResponses = 0;
        
        // Create a message and broadcast it to the nodes
        ClientMessage clientMessage = new ClientMessage(clientConfig.getId(), Message.Type.BALANCE);
        BalanceMessage balanceMessage = new BalanceMessage(nodeId);
        clientMessage.setMessage(balanceMessage.toJson());

        linkToNodes.broadcast(clientMessage);
    }

    public void uponTransferResponse(ClientMessage message) {
        TResponseMessage transferResponse = message.deserializeTResponseMessage();
        Transaction transaction = Transaction.fromJson(transferResponse.getTransaction());
        int nonce = transaction.getNonce();
        boolean responseCompleted = true;

        switch (transferResponse.getStatus()) {
            case FAILED_BALANCE:
                System.out.println(MessageFormat.format(
                        "{0} - Transfer Failed, from node {1}. Not enough balance to transfer amount {2} to client {3}",
                        clientConfig.getId(), message.getSenderId(),
                        transaction.getAmount(), transaction.getReceiver()));
                break;

            case FAILED_RECEIVER:
                System.out.println(MessageFormat.format(
                        "{0} - Transfer Failed, from node {1}. Client {2} does not exist",
                        clientConfig.getId(), message.getSenderId(),
                        transaction.getReceiver()));
                break;

            case FAILED_SENDER:
                System.out.println(MessageFormat.format(
                        "{0} - Transfer Failed, from node {1}. Sender and receiver are the same",
                        clientConfig.getId(), message.getSenderId()));
                break;

            case FAILED_SIGNATURE:
                System.out.println(MessageFormat.format(
                        "{0} - Transfer Failed, from node {1}. Signature mismatch",
                        clientConfig.getId(), message.getSenderId()));
                break;

            case FAILED_REPEATED:
                System.out.println(MessageFormat.format(
                        "{0} - Transfer Failed, from node {1}. Repeated nonce",
                        clientConfig.getId(), message.getSenderId()));
                break;

            case SUCCESS:
                // Adds response to the list, and checks how many it has
                transferResponses.computeIfAbsent(nonce, key -> new ArrayList<>())
                        .add(transferResponse);
                // Prints when its exactly f+1
                responseCompleted = transferResponses.get(nonce).size() == smallQuorumSize;
                if (responseCompleted) {
                    System.out.println(MessageFormat.format(
                            "{0} - Transfer Completed from f+1 nodes in position {1}. Transfer amount: {2}. Transfer recipient: {3}",
                            clientConfig.getId(), transferResponse.getPosition(),
                            transaction.getAmount(), transaction.getReceiver())
                    );
                }
                break;

            default:
                System.out.println(MessageFormat.format(
                        "{0} - Transfer Failed, from node {1}. Reason is unknown.",
                        clientConfig.getId(), message.getSenderId()));
                break;


        }
        if (responseCompleted) {
            System.out.println();
            System.out.print(">> ");
        }
    }

    public void uponBalanceResponse(ClientMessage message) {
        BResponseMessage balanceResponse = message.deserializeBResponseMessage();
        String nodeId = balanceResponse.getNodeId();
        
        // Adds response
        balanceResponses++;

        // Prints when its exactly f+1
        if (balanceResponses == smallQuorumSize) {
            switch (balanceResponse.getStatus()) {
                case SUCCESS:
                    System.out.println(MessageFormat.format(
                            "{0} - Client {1} has a balance of {2,number,#.#######}",
                            clientConfig.getId(), nodeId, balanceResponse.getBalance())
                    );
                    break;
                case FAILED_ID:
                    System.out.println(MessageFormat.format(
                            "{0} - Balance Failed. Client {1} does not exist",
                            clientConfig.getId(), nodeId)
                    );
                    break;
                default:
                    System.out.println(MessageFormat.format(
                            "{0} - Balance Failed. Reason is unknown.",
                            clientConfig.getId())
                    );
                    break;
            }
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
