package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class ConsensusMessage extends Message {

    // Consensus instance
    private int consensusInstance;
    // Round
    private int round;
    // Who sent the previous message
    private String replyTo;
    // Id of the previous message
    private int replyToMessageId;
    // Message (PREPREPARE, PREPARE, COMMIT, ROUND-CHANGE)
    private String message;
    // Justification for PREPREPARE and ROUND-CHANGE messages
    private List<ConsensusMessage> justification = new ArrayList<>();

    public ConsensusMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public PrePrepareMessage deserializePrePrepareMessage() {
        return new Gson().fromJson(this.message, PrePrepareMessage.class);
    }

    public PrepareMessage deserializePrepareMessage() {
        return new Gson().fromJson(this.message, PrepareMessage.class);
    }

    public CommitMessage deserializeCommitMessage() {
        return new Gson().fromJson(this.message, CommitMessage.class);
    }

    public RoundChangeMessage deserializeRoundChangeMessage() {
        return new Gson().fromJson(this.message, RoundChangeMessage.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public int getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setJustification(List<ConsensusMessage> justification) {
        this.justification = justification;
    }

    public List<ConsensusMessage> getJustification() {
        return justification;
    }

    public void setReplyToMessageId(int replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }
    // TODO - add a nounce?
    @Override
    public String getSignable(){
        return super.getSenderId() + super.getMessageId() + super.getType().toString()
                + getConsensusInstance() + getRound() + getReplyTo() + getReplyToMessageId() + getMessage();
    }
}

