import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.zone.ZoneOffsetTransitionRule;
import java.util.*;

public class ServerNode {

    private int localTime;
    private ArrayList<String> serverNodes;
    private Hashtable<String, Socket> serverSockets;
    private Hashtable<String, Socket> clientSockets;
    private String name;
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private PriorityQueue<Message> commands;


    public ServerNode(String name, ArrayList<String> ipsAndPorts) {
        localTime = 0;
        this.name = name;
        serverNodes = ipsAndPorts;
        serverSockets = new Hashtable<>();
        commands = new PriorityQueue<>();
    }


    public void up() {
        var listenThread = new Thread(() -> {
            try {
                listenForIncomingMessages();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listenThread.start();


    }


    // among servers only for now
    private void listenForIncomingMessages() throws IOException {
        int PORT = 1234;
        var serverSocket = new ServerSocket(PORT);
        Socket incomingSocket;

        while (true) {
            incomingSocket = serverSocket.accept();
            var finalSocket = incomingSocket;

            System.out.println("New request received : " + incomingSocket);

            var name = String.format("%s:%s", incomingSocket.getInetAddress(), incomingSocket.getPort());

            if (serverNodes.contains(name)) {
                serverSockets.put(name, incomingSocket);

                var thread = new Thread(() -> {
                    try {
                        handleServerServerCommunication(name, finalSocket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                thread.start();
            } else {
                clientSockets.put(name, incomingSocket);

                var thread = new Thread(() -> {
                    try {
                        handleClientServerCommunication(name, finalSocket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                thread.start();
            }
        }
    }

    private void handleServerServerCommunication(String name, Socket socket) throws IOException {
        var communicationOn = true;
        var dis = new DataInputStream(socket.getInputStream());
        var dos = new DataOutputStream(socket.getOutputStream());

        while (communicationOn) {
            try {
                var receivedMessageString = dis.readUTF();
                incrementLocalTime();

                System.out.println(receivedMessageString);
                var receivedMessage = new Message(receivedMessageString);

                if (receivedMessage.getType() == Message.MessageType.WriteAcquireRequest) {
                    commands.add(receivedMessage);
                    setLocalTime(receivedMessage.getTimeStamp());

                    var responseMessage = new Message(this.name, Message.MessageType.WriteAcquireResponse, localTime, "");
                    dos.writeUTF(responseMessage.toString());
                } else if (receivedMessage.getType() == Message.MessageType.WriteReleaseRequest) {
                    commands.removeIf(m -> m.getType() == Message.MessageType.WriteAcquireRequest && m.getSenderName().equals(receivedMessage.getSenderName()));
                }
            } catch (Exception e) {
                communicationOn = false;
            }
        }

        dis.close();
        dos.close();
    }

    private void handleClientServerCommunication(String name, Socket socket) throws IOException {

    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }

}
