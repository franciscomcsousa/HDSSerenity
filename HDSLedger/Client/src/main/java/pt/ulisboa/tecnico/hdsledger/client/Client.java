package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;

//
//  Client who sends requests to the blockchain
//
public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());
    // Hardcoded path to files
    private static String clientsConfigPath = "src/main/resources/";
    private static String nodesConfigPath = "../Service/src/main/resources/";


    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            clientsConfigPath += args[1];
            nodesConfigPath += args[2];

            // Create configuration instances
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
            ProcessConfig clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId().equals(id)).findAny().get();

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}",
                    clientConfig.getId(), clientConfig.getHostname(), clientConfig.getPort()));

            // Nodes in Blockchain instances
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            for(ProcessConfig n : nodeConfigs) {
                n.setPort(n.getClientPort());
            }

            // Create the client library and wait for replies
            final ClientLibrary clientLibrary = new ClientLibrary(clientConfig, nodeConfigs);
            clientLibrary.listen();
            
            // Read user input
            Scanner scanner = new Scanner(System.in);
            String input = "";
            
            System.out.println("Client started. \n" +
                    "Type 'transfer <destination> <value>' to make a transfer. \n" +
                    "Type 'balance <account id>' to check the balance. \n" +
                    "Type 'quit' to quit.");
            System.out.println();
            System.out.print(">> ");

            while(true){
                
                input = scanner.nextLine();

                // If the input is empty, ignore it
                if(input.isEmpty()) continue;

                String[] splitInput = input.split(" ");

                // Case for each user input
                switch(splitInput[0]){
                    case "transfer": case "t":
                        if (splitInput.length != 3) {
                            System.out.println("Invalid command. Type 'transfer <destination> <value>' to make a transfer.");
                            break;
                        }
                        //clientLibrary.append(splitInput[1]);
                        clientLibrary.transfer(id, splitInput[1], Integer.valueOf(splitInput[2]));
                        break;
                    case "quit":
                        scanner.close();
                        System.exit(0);
                    default:
                        System.out.println("Invalid command. Type 'transfer <destination> <value>' to make a transfer. " +
                            "Type 'balance <account id>' to check the balance. Type 'quit' to quit.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
