package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.RequestMessage;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CollapsingSet;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String nodesConfigPath = "src/main/resources/";


    // currently will only work for one client ! -> since it will be a node client library is OK
    // TODO
    private static String clientConfigPath = "../Client/src/main/resources/client_config.json";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            nodesConfigPath += args[1];
            // Create configuration instances
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
            ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny().get();

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; is leader: {3}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                    nodeConfig.isLeader()));

            // Create client configs (only works for one client) -> eventually will be the client library
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientConfigPath);
            ProcessConfig clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId().equals("20")).findAny().get();

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), nodeConfigs,
                    ConsensusMessage.class);

            // Services that implement listen from UDPService
            // Listen to the nodes in the blockChain
            NodeService nodeService = new NodeService(linkToNodes, nodeConfig, leaderConfig,
                    nodeConfigs);

            // TODO - future implementation for library
            //Link linkToClient = new Link(nodeConfig, 4000 + Integer.parseInt(nodeConfig.getId()), clientConfigs,
            //        RequestMessage.class);
            // Listen to the clients requests
            //NodeService clientService = new NodeService(linkToClient, clientConfig, leaderConfig,
            //        clientConfigs);

            nodeService.startConsensus("teste");
            nodeService.listen();
            //clientService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
