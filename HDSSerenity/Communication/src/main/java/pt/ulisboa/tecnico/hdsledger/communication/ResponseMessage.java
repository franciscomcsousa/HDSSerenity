package pt.ulisboa.tecnico.hdsledger.communication;

public class ResponseMessage {

    // String value
    private String message;

    // Position in the blockchain
    private int position;

    public ResponseMessage(String value, int position) {
        this.message = value;
        this.position = position;
    }

    public String getMessage() {
        return message;
    }

    public void setValue(String value) {
        this.message = value;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
