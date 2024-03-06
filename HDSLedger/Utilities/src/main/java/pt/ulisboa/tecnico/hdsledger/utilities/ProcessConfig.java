package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    public ProcessConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

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


}
