import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;

public class ClientNode {
    private int localTime;
    private Hashtable<String, Socket> serverSockets;
    private String name;
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;

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

            serverSockets.put(server, new Socket(serverIp, serverPort));
        }
    }

    public void up() {
        // single write for now, but will put in loop and random wait time later
    }

    private void requestWrite(String server, String messagePayload) throws IOException {
        var socket = serverSockets.get(server);

        incrementLocalTime();

        var message = new Message(this.name, Message.MessageType.ClientWriteRequest, localTime, messagePayload);
        sendMessage(socket, message.toString());

        var dis = new DataInputStream(socket.getInputStream());
        var responseMessageText = dis.readUTF();
        var responseMessage = new Message(responseMessageText);
        System.out.printf("Receiving '%s' from (%s:%d)\n", responseMessageText, socket.getInetAddress(), socket.getPort());

        incrementLocalTime();
        setLocalTime(responseMessage.getTimeStamp());
    }

    private void sendMessage(Socket socket, String message) throws IOException {
        System.out.printf("Sending '%s' to '(%s:%d)...'\n", message, socket.getInetAddress(), socket.getPort());

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
