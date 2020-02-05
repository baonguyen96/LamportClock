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
        var random = new Random();
        String message;
        int fileNumber;
        int serverNumber;

        for(var i = 0; i < 20; i++) {
            fileNumber = random.nextInt(1) + 1;
            message = String.format("File%d.txt|(%s) writes line %d", fileNumber, this.name, i);

            serverNumber = random.nextInt(serverSockets.size());
            requestWrite((String) serverSockets.keySet().toArray()[serverNumber], message);

            Thread.sleep(random.nextInt(1000));
        }
    }

    private void requestWrite(String server, String messagePayload) throws IOException {
        var socket = serverSockets.get(server);
        var message = new Message(this.name, Message.MessageType.ClientWriteRequest, localTime, messagePayload);

        incrementLocalTime();

        sendMessage(socket, message.toString());

        var dis = new DataInputStream(socket.getInputStream());
        var responseMessageText = dis.readUTF();
        var responseMessage = new Message(responseMessageText);
        Logger.log(String.format("Receiving '%s' from (%s:%d)", responseMessageText, socket.getInetAddress(), socket.getPort()));

        setLocalTime(responseMessage.getTimeStamp());
        incrementLocalTime();
    }

    private void sendMessage(Socket socket, String message) throws IOException {
        Logger.log(String.format("Sending '%s' to (%s:%d)...", message, socket.getInetAddress(), socket.getPort()));

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
