package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    public ProcessConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

    private Behavior behavior = Behavior.NONE;

    public enum Behavior {
        NONE("NONE"),
        FAULTY("FAULTY");

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
