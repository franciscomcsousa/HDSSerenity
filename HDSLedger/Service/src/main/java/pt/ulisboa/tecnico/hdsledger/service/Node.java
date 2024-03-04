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

    // Hardcoded path to nodes and clients files
    private static String nodesConfigPath = "src/main/resources/";
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

            // Combine the node and client configs into one ProcessConfig array
            ProcessConfig[] allConfigs = new ProcessConfig[nodeConfigs.length + clientConfigs.length];
            System.arraycopy(nodeConfigs, 0, allConfigs, 0, nodeConfigs.length);
            System.arraycopy(clientConfigs, 0, allConfigs, nodeConfigs.length, clientConfigs.length);

            // Abstraction to send and receive messages
            Link linkToAllNodes = new Link(nodeConfig, nodeConfig.getPort(), allConfigs,
                    ConsensusMessage.class);

            // Services that implement listen from UDPService
            // Listen to the nodes in the blockChain
            NodeService nodeService = new NodeService(linkToAllNodes, nodeConfig, leaderConfig,
                    allConfigs);

            nodeService.startConsensus("a");
            // TODO - future implementation for library
            
            // Start listening for requests
            nodeService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
