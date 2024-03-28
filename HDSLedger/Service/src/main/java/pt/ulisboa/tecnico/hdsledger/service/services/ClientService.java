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

    public ProcessConfig getConfig() {
        return this.config;
    }

    private void receivedTransfer(ClientMessage message) {
        TransferMessage transferMessage = message.deserializeTransferMessage();
        String receiverId = transferMessage.getReceiver();

        // Creates Transaction
        Transaction newTransaction = new Transaction(transferMessage.getSender(),receiverId,transferMessage.getAmount());

        // TODO - add logic that verifies the validity of the
        //  transfer before creating transactions and adding to the requests
        //Map<String, Integer> clientsBalance = nodeService.getClientsBalance();
        // Verify ReceiverID
        if(Arrays.stream(clientConfigs).noneMatch(clientId -> clientId.getId().equals(receiverId))){
            // TODO - add response error message
            //System.out.println(" Detected error in transfer, with receiver");
        }

        nodeService.newTransferRequest(newTransaction);

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
