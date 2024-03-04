package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.RequestMessage;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.RSASignature;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

//
//  not a real client, temporary base for testing purposes
//
public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());
    // Hardcoded path to files
    private static String clientsConfigPath = "src/main/resources/";
    private static String nodesConfigPath = "../Service/src/main/resources/node_config.json";


    public static void main(String[] args) {

        try {
            // Command line arguments, [id, hostname, port]
            String id = args[0];
            clientsConfigPath += args[1];

            // Create configuration instances
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
            ProcessConfig clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId().equals(id)).findAny().get();

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}",
                    clientConfig.getId(), clientConfig.getHostname(), clientConfig.getPort()));

            // Nodes in Blockchain instances
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();

            System.out.println(clientConfig.getPort());

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, RequestMessage.class);
            // Services that implement listen from UDPService
            NodeService clientService = new NodeService(linkToNodes, clientConfig, leaderConfig,
                    nodeConfigs);

            RequestMessage requestMessage = new RequestMessage(clientConfig.getId(), Message.Type.APPEND, "teste");

            // Signature
            byte[] signature = RSASignature.sign(requestMessage.toString(), clientConfig.getId());

            // TODO - future implementation for library communications
            linkToNodes.send(leaderConfig.getId(),requestMessage);
            Thread.sleep(5000);
            requestMessage.setMessage("aaaaa");
            linkToNodes.send(leaderConfig.getId(),requestMessage);
            clientService.listen();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
