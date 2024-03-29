package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // Link to communicate with nodes
    private final Link link;

    // (Self) node config
    private final ProcessConfig config;

    // Nodes configurations
    private final ProcessConfig[] nodesConfigs;

    // Leader configuration
    private ProcessConfig[] clientConfigs;

    // Node Service
    private NodeService nodeService;

    public ClientService(Link link, ProcessConfig config,
                       ProcessConfig[] nodesConfigs, ProcessConfig[] clientConfigs, NodeService nodeService) {

        this.link = link;
        this.config = config;
        this.clientConfigs = clientConfigs;
        this.nodesConfigs = nodesConfigs;
        this.nodeService = nodeService;
    }

    private void receivedTransfer(ClientMessage message) {
        TransferMessage transferMessage = message.deserializeTransferMessage();
        String receiverId = transferMessage.getReceiver();
        String senderId = transferMessage.getSender();
        Integer amount = transferMessage.getAmount();
        Integer nonce = transferMessage.getNonce();

        // Creates Transaction
        Transaction newTransaction = new Transaction(senderId,receiverId,amount,nonce);

        // Verify ReceiverID, does it exist?
        if (Arrays.stream(clientConfigs).noneMatch(clientId -> clientId.getId().equals(receiverId))){
            TResponseMessage tResponseMessage = new TResponseMessage(newTransaction.toJson(), TResponseMessage.Status.FAILED_RECEIVER);
            ClientMessage clientMessage = new ClientMessage(config.getId(), Message.Type.TRANSFER_RESPONSE);
            clientMessage.setMessage(tResponseMessage.toJson());

            link.send(message.getSenderId(), clientMessage);
            return;
        }

        // Verify SenderID different from ReceiverID
        if (receiverId.equals(senderId)){
            TResponseMessage tResponseMessage = new TResponseMessage(newTransaction.toJson(), TResponseMessage.Status.FAILED_SENDER);
            ClientMessage clientMessage = new ClientMessage(config.getId(), Message.Type.TRANSFER_RESPONSE);
            clientMessage.setMessage(tResponseMessage.toJson());

            link.send(message.getSenderId(), clientMessage);
            return;
        }

        nodeService.newTransferRequest(newTransaction);
    }

    private void receivedBalance(ClientMessage message) {
        String senderId = message.getSenderId();
        int balance = nodeService.getBalance(senderId);

        // Create message with balance
        BResponseMessage bResponseMessage = new BResponseMessage(balance);
        ClientMessage clientMessage = new ClientMessage(config.getId(), Message.Type.BALANCE_RESPONSE);
        clientMessage.setMessage(bResponseMessage.toJson());

        link.send(senderId, clientMessage);
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {

                        Message message = link.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {
                                case TRANSFER ->
                                {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received TRANSFER message from {1}",
                                            config.getId(), message.getSenderId()));

                                    receivedTransfer((ClientMessage) message);
                                }

                                case BALANCE ->
                                {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received BALANCE message from {1}",
                                            config.getId(), message.getSenderId()));
                                    receivedBalance((ClientMessage) message);
                                }

                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                            config.getId(), message.getSenderId()));

                                case IGNORE ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    config.getId(), message.getSenderId()));

                                case INVALID ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received INVALID message from {1}",
                                                    config.getId(), message.getSenderId()));

                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));
                            }

                        }).start();
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
