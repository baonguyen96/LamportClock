import java.util.Arrays;
import java.util.StringTokenizer;

public class Message {

    public enum MessageType {
        WriteAcquireRequest,
        WriteAcquireResponse,
        WriteReleaseRequest
    }

    private String senderName;
    private int timeStamp;
    private String payload;
    private MessageType type;

    public Message(String senderName, MessageType type, int timeStamp, String payload) {
        this.senderName = senderName;
        this.timeStamp = timeStamp;
        this.payload = payload;
        this.type = type;
    }

    public Message(String messageAsString) {
        var tokenizer = new StringTokenizer(messageAsString, "|");
        senderName = tokenizer.nextToken();
        type = MessageType.valueOf(tokenizer.nextToken());
        timeStamp = Integer.parseInt(tokenizer.nextToken());

        var sb = new StringBuilder();
        while(tokenizer.hasMoreTokens()) {
            sb.append(tokenizer.nextToken());
        }
        payload = sb.toString();
    }

    public String getSenderName() {
        return senderName;
    }

    public MessageType getType() {
        return type;
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return String.format("%s|%s|%d|%s", senderName, type.toString(), timeStamp, payload);
    }
}
