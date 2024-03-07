package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RequestMessage {

    // String value
    private String message;

    public RequestMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
