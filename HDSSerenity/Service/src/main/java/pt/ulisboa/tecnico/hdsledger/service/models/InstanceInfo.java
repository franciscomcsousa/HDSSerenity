package pt.ulisboa.tecnico.hdsledger.service.models;


import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;

public class InstanceInfo {

    private int currentRound = 1;
    private int preparedRound = -1;
    private Block preparedBlock;
    private CommitMessage commitMessage;
    private int committedRound = -1;
    private int latestRoundChange = -1;
    private int latestRoundChangeBroadcast = -1;

    public InstanceInfo() { }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public void incrementCurrentRound() { this.currentRound++; }

    public int getPreparedRound() {
        return preparedRound;
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

    public Block getPreparedBlock() {
        return preparedBlock;
    }

    public void setPreparedBlock(Block preparedBlock) {
        this.preparedBlock = preparedBlock;
    }

    public int getCommittedRound() {
        return committedRound;
    }

    public void setCommittedRound(int committedRound) {
        this.committedRound = committedRound;
    }

    public CommitMessage getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(CommitMessage commitMessage) {
        this.commitMessage = commitMessage;
    }

    public int getLatestRoundChange() {
        return latestRoundChange;
    }

    public void setLatestRoundChange(int latestRoundChange) {
        this.latestRoundChange = latestRoundChange;
    }

    public int getLatestRoundChangeBroadcast() {
        return latestRoundChangeBroadcast;
    }

    public void setLatestRoundChangeBroadcast(int latestBroadcastRoundChange) {
        this.latestRoundChangeBroadcast = latestBroadcastRoundChange;
    }
}
