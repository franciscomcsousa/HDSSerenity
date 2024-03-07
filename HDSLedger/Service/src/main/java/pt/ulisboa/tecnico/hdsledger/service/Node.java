package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());

    // Hardcoded path to nodes and clients files
    private static String nodesConfigPath = "src/main/resources/";
    private static String clientConfigPath = "../Client/src/main/resources/";
    private static NodeService nodeService;

    // Class needs to be created in order for the scheduler do use it
    // runs the roundChange
    public static class RoundTimer extends TimerTask {
        // Does NOT create a new thread !
        @Override
        public void run() {
            System.out.println("======== TIMER EXPIRED ========\n\n");

            // should not be a problem, because of there's only ONE TIMER in each nodeService
            // even though there might be multiple threads doing other things

            // not sure if this is needed
            //Node.nodeService.notify();
            Node.nodeService.uponTimerExpiry();

        }
    }

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            nodesConfigPath += args[1];
            clientConfigPath += args[2];
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
            nodeService = new NodeService(linkToAllNodes, nodeConfig, leaderConfig,
                    allConfigs);
            
            // Start listening for requests

                nodeService.listen();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
