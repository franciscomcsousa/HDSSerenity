package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.Node;
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
                                case APPEND ->
                                {
                                    nodeService.addClientMessage(message.getSenderId(), ((ClientMessage) message).getMessage());
                                    nodeService.startConsensus((ClientMessage) message);
                                }

                                case ACK ->
                                        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                                config.getId(), message.getSenderId()));

                                case IGNORE ->
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Received IGNORE message from {1}",
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
