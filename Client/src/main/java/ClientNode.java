import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.StringTokenizer;

public class ClientNode {
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private int localTime;
    private String name;
    private Hashtable<String, Socket> serverSockets;
    private Logger logger = new Logger(Logger.LogLevel.Release);

    public ClientNode(String name, ArrayList<String> servers) throws IOException {
        this.name = name;
        localTime = 0;
        serverSockets = new Hashtable<>();
        populateServerSockets(servers);
    }

    private void populateServerSockets(ArrayList<String> servers) throws IOException {
        for (var server : servers) {
            var tokenizer = new StringTokenizer(server, ":");
            var serverIp = InetAddress.getByName(tokenizer.nextToken());
            var serverPort = Integer.parseInt(tokenizer.nextToken());
            var socket = new Socket(serverIp, serverPort);
            serverSockets.put(server, socket);

            sendMessage(socket,String.format("Client %s", this.name));
        }
    }

    public void up() throws IOException, InterruptedException {
        logger.log(String.format("Client %s is up", this.name));

        var random = new Random();
        String message;
        int fileNumber;
        int serverNumber;
        String serverName;

        for(var i = 0; i < 20; i++) {
            serverNumber = random.nextInt(serverSockets.size());
            serverName = (String) serverSockets.keySet().toArray()[serverNumber];
            fileNumber = random.nextInt(4) + 1;
            message = String.format("File%d.txt|%s sends message #%d to server %s", fileNumber, this.name, i, serverName);

            requestWrite(serverName, message);
            Thread.sleep(random.nextInt(1000));
        }

        logger.log(String.format("Client '%s' gracefully exits", this.name));
    }

    private void requestWrite(String server, String messagePayload) throws IOException {
        incrementLocalTime();

        var socket = serverSockets.get(server);
        var message = new Message(this.name, Message.MessageType.ClientWriteRequest, localTime, messagePayload);

        sendMessage(socket, message.toString());

        var dis = new DataInputStream(socket.getInputStream());
        var responseMessageText = dis.readUTF();
        var responseMessage = new Message(responseMessageText);

        logger.log(String.format("Receiving '%s' from server '%s'", responseMessageText, socket));

        setLocalTime(responseMessage.getTimeStamp());
        incrementLocalTime();
    }

    private void sendMessage(Socket socket, String message) throws IOException {
        logger.log(String.format("Sending '%s' to server '%s'...", message, socket));

        var dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(message);
    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }
}
