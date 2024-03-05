package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RoundChangeMessage {

    // Prepared round
    private int preparedRound;

    // Prepared value
    private String preparedValue;

    public RoundChangeMessage(int preparedRound, String preparedValue){
        this.preparedRound = preparedRound;
        this.preparedValue = preparedValue;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public String getPreparedValue() {
        return preparedValue;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
