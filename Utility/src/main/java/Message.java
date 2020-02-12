import java.util.StringTokenizer;

public class Message implements Comparable<Message> {

    public enum MessageType {
        WriteAcquireRequest,
        WriteAcquireResponse,
        WriteSyncRequest,
        WriteReleaseRequest,
        WriteSuccessAck,
        WriteFailureAck,
        ClientWriteRequest,
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

            if(tokenizer.hasMoreTokens()) {
                sb.append("|");
            }
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

    public String getFileNameFromPayload() {
        return payload.split("\\|")[0];
    }

    public String getDataFromPayload() {
        return payload.substring(payload.indexOf('|') + 1);
    }

    @Override
    public int compareTo(Message o) {
        var result = Integer.compare(this.timeStamp, o.timeStamp);

        if(result == 0) {
            result = this.senderName.compareTo(o.senderName);
        }

        return result;
    }

    @Override
    public String toString() {
        return String.format("%s|%s|%d|%s", senderName, type.toString(), timeStamp, payload);
    }
}
