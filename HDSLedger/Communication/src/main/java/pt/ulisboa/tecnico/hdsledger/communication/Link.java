package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class Link {

    private static final CustomLogger LOGGER = new CustomLogger(Link.class.getName());
    /** Time to wait for an ACK before resending the message */
    private final int BASE_SLEEP_TIME;
    /** UDP Socket */
    private final DatagramSocket socket;
    /** Map of all nodes in the network */
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    /** Reference to the node itself */
    private final ProcessConfig config;
    /** Class to deserialize messages to */
    private final Class<? extends Message> messageClass;
    /** Set of received messages from specific node (prevent duplicates) */
    private final Map<String, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    /** Set of received ACKs from specific node */
    private final CollapsingSet receivedAcks = new CollapsingSet();
    /** Message counter */
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    /** Send messages to self by pushing to queue instead of through the network */
    private final Queue<Message> localhostQueue = new ConcurrentLinkedQueue<>();

    public Link(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this(self, port, nodes, messageClass, false, 200);
    }

    public Link(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
            boolean activateLogs, int baseSleepTime) {

        this.config = self;
        this.messageClass = messageClass;
        this.BASE_SLEEP_TIME = baseSleepTime;

        Arrays.stream(nodes).forEach(node -> {
            String id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new HDSSException(ErrorMessage.CannotOpenSocket);
        }
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /**
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcasted
     */
    public void broadcast(Message data) {
        Gson gson = new Gson();

        nodes.forEach((destId, dest) -> {
            // If it isn't a client node, broadcast the message
                send(destId, gson.fromJson(gson.toJson(data), data.getClass()));
            }
        );
    }

    /**
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    public void send(String nodeId, Message data) {

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null)
                    throw new HDSSException(ErrorMessage.NoSuchNode);

                if (data.getMessageId() == -1) {
                    data.setMessageId(messageCounter.getAndIncrement());
                }

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());
                int destPort = node.getPort();
                int count = 1;
                int messageId = data.getMessageId();
                int sleepTime = BASE_SLEEP_TIME;

                // sign the message and get the signature
                byte[] signature;

                // If a message was once signed, can't be signed again
                if (data.getSignature() == null) {
                    signature = RSASignature.sign(data.getSignable(), data.getSenderId());
                    data.setSignature(signature);
                }

                // Send message to local queue instead of using network if destination in self
                if (nodeId.equals(this.config.getId())) {
                    this.localhostQueue.add(data);

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} successfully",
                                    config.getId(), data.getType(), destAddress, destPort));

                    return;
                }

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++));

                    unreliableSend(destAddress, destPort, data);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} successfully",
                        config.getId(), data.getType(), destAddress, destPort));
            } catch (InterruptedException | UnknownHostException e) {
                e.printStackTrace();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    /**
     * Sends a message to a specific node without guarantee of delivery
     * Mainly used to send ACKs, if they are lost, the original message will be
     * resent
     *
     * @param hostname The address of the destination node
     *
     * @param port The port of the destination node
     *
     * @param data The message to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, Message data) {
        new Thread(() -> {
            try {
                byte[] buf = new Gson().toJson(data).getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }

    /**
     * Receives a message from any node in the network (blocking)
     *
     * @return Message - received message
     * @throws Exception exception
     */
    public Message receive() throws Exception {

        Message message = null;
        byte[] signature = null;
        String serialized = "";
        Boolean local = false;
        DatagramPacket response = null;

        if (this.localhostQueue.size() > 0) {
            message = this.localhostQueue.poll();
            local = true; 
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65535];
            response = new DatagramPacket(buf, buf.length);

            socket.receive(response);
            // signature is appended to the message, since is sha2 is 512 bytes
            byte[] messageBuffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            serialized = new String(messageBuffer);
            // If the sender is a client (id >= 20), deserialize the message to a RequestMessage
            message = new Gson().fromJson(serialized, Message.class);
        }

        signature = message.getSignature();

        // Data creation: message + signature
        // Data data = new Data(message, signature);

        String senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (!nodes.containsKey(senderId))
            throw new HDSSException(ErrorMessage.NoSuchNode);


        // Handle ACKS, since it's possible to receive multiple acks from the same
        // message
        if (message.getType().equals(Type.ACK)) {
            receivedAcks.add(messageId);
            return message;
        }

        // It's not an ACK -> Deserialize for the correct type (if not a client who sent)
        if (!local) {
            message = new Gson().fromJson(serialized, this.messageClass);
        }

        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        Type originalType = message.getType();
        // Message already received (add returns false if already exists) => Discard
        if (isRepeated) {
            message.setType(Type.IGNORE);
        }

        // Local messages don't have a need to verify the signature
        if (!local) {
            // If signature doesn't match, message is set to INVALID
            if (!RSASignature.verifySign(message.getSignable(), signature, message.getSenderId())) {
                message.setType(Type.INVALID);
            }
        }

        switch (message.getType()) {
            case PRE_PREPARE, INVALID -> {
                return message;
            }
            case IGNORE -> {
                if (!originalType.equals(Type.COMMIT))
                    return message;
            }
            case PREPARE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());

                return message;
            }
            case COMMIT, ROUND_CHANGE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());
            }
            default -> {}
        }

        if (!local) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Type.ACK);
            responseMessage.setMessageId(messageId);

            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates

            byte[] ackSignature = RSASignature.sign(responseMessage.getSignable(), responseMessage.getSenderId());
            responseMessage.setSignature(ackSignature);

            unreliableSend(address, port, responseMessage);
        }
        
        return message;
    }
}
