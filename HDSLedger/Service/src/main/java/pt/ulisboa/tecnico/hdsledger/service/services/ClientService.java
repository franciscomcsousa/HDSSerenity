package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Requests;
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

    // Requests queue
    private final Requests requests;

    public ClientService(Link link, ProcessConfig config,
                       ProcessConfig[] nodesConfigs, ProcessConfig[] clientConfigs, NodeService nodeService, Requests requests) {

        this.link = link;
        this.config = config;
        this.clientConfigs = clientConfigs;
        this.nodesConfigs = nodesConfigs;
        this.nodeService = nodeService;
        this.requests = requests;
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    private void receivedTransfer(ClientMessage message) {
        // Add the request to the queue
        requests.addRequest(message);
        
        if (requests.isEnoughRequests()) {
            nodeService.startConsensus(requests.createBlock());
        }
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
