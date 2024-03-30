package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    public ProcessConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

    private int clientPort;

    private Behavior behavior = Behavior.NONE;

    public enum Behavior {
        NONE("NONE"),
        FAULTY("FAULTY"),
        DIFF_VALUE("DIFF_VALUE"),
        MULT_LEADERS("MULT_LEADERS"),
        PREPARE_VALUE("PREPARE_VALUE"),
        COMMIT_VALUE("COMMIT_VALUE"),
        NO_COMMIT("NO_COMMIT"),
        NODE_REPLAY_ATTACK("NODE_REPLAY_ATTACK"),
        CLIENT_REPLAY_ATTACK("CLIENT_REPLAY_ATTACK"),;

        String behavior;

        Behavior(String behavior) {
            this.behavior = behavior;
        }
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void setLeader(boolean newIsLeader) {
        this.isLeader = newIsLeader;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int newPort) {
        port = newPort;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int newClientPort) {
        clientPort = newClientPort;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public void setBehavior(Behavior newBehavior) {
        behavior = newBehavior;
    }

    public Behavior getBehavior() {
        return behavior;
    }

}
