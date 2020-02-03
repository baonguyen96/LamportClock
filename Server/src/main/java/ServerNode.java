import java.io.*;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class ServerNode {
    private int localTime;
    private ArrayList<String> serverNodes;
    private Hashtable<String, Socket> serverSockets;
    private Hashtable<String, Boolean> acquiredLockServerSockets;
    private Hashtable<String, Socket> clientSockets;
    private String name;
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private PriorityQueue<Message> commandsQueue;
    private String directoryPath;


    public ServerNode(String name, ArrayList<String> ipsAndPorts, String directoryPath) {
        localTime = 0;
        this.name = name;
        serverNodes = ipsAndPorts;
        serverSockets = new Hashtable<>();
        acquiredLockServerSockets = new Hashtable<>();
        commandsQueue = new PriorityQueue<>();
        clientSockets = new Hashtable<>();
        this.directoryPath = directoryPath;
    }


    public void up() throws IOException {
        listenForIncomingMessages();
    }


    // among servers only for now
    private void listenForIncomingMessages() throws IOException {
        var tokenizer = new StringTokenizer(this.name, ":");
        var ipAddress = InetAddress.getByName(tokenizer.nextToken());
        var portNumber = Integer.parseInt(tokenizer.nextToken());
        var serverSocket = new ServerSocket(portNumber, 100, ipAddress);
        Socket incomingSocket;

        System.out.printf("ServerSocket is up and bound to (%s)\n", this.name);

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
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                thread.start();
            }
            else {
                clientSockets.put(name, incomingSocket);

                var thread = new Thread(() -> {
                    try {
                        handleClientServerCommunication(name, finalSocket);
                    }
                    catch (IOException e) {
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

        while (communicationOn) {
            try {
                var receivedMessageString = dis.readUTF();
                incrementLocalTime();

                System.out.printf("Receiving '%s' from (%s:%d)\n", receivedMessageString, socket.getInetAddress(), socket.getPort());
                var receivedMessage = new Message(receivedMessageString);

                if (receivedMessage.getType() == Message.MessageType.WriteAcquireRequest) {
                    commandsQueue.add(receivedMessage);
                    setLocalTime(receivedMessage.getTimeStamp());

                    var responseMessage = new Message(this.name, Message.MessageType.WriteAcquireResponse, localTime, "");
                    sendMessage(socket, responseMessage.toString());
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteReleaseRequest) {
                    commandsQueue.removeIf(m -> m.getType() == Message.MessageType.WriteAcquireRequest && m.getSenderName().equals(receivedMessage.getSenderName()));
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteSyncRequest) {
                    var fileName = receivedMessage.getFileNameFromPayload();
                    var lineToAppend = receivedMessage.getDataFromPayload();
                    appendToFile(fileName, lineToAppend);
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteAcquireResponse) {
                    acquiredLockServerSockets.put(name, false);
                }
            }
            catch (Exception e) {
                communicationOn = false;
            }
        }

        dis.close();
    }

    private void handleClientServerCommunication(String name, Socket socket) throws IOException {
        var communicationOn = true;
        var dis = new DataInputStream(socket.getInputStream());

        while (communicationOn) {
            try {
                var receivedMessageString = dis.readUTF();
                incrementLocalTime();

                System.out.printf("Receiving '%s' from (%s:%d)\n", receivedMessageString, socket.getInetAddress(), socket.getPort());
                var receivedMessage = new Message(receivedMessageString);

                setLocalTime(receivedMessage.getTimeStamp());

                var writeRequestMessage = new Message(this.name, Message.MessageType.WriteAcquireRequest, localTime, "");
                for(var serverName : serverSockets.keySet()) {
                    var serverSocket = serverSockets.get(serverName);
                    sendMessage(serverSocket, writeRequestMessage.toString());
                    acquiredLockServerSockets.put(serverName, false);
                }

                // wait until all confirmed to be proceeding
                while(!isAllConfirmToAllowEnterCriticalSession()) {
                    Thread.sleep(100);
                }

                // enter critical session if my write request is the first in the queue
                processCriticalSession(receivedMessage);

                var responseMessage = new Message(this.name, Message.MessageType.WriteComplete, localTime, "");
                sendMessage(socket, responseMessage.toString());
            }
            catch (Exception e) {
                communicationOn = false;
            }
        }

        dis.close();
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

    private synchronized boolean isLocalWriteRequestFirstInQueue() {
        var top = commandsQueue.peek();
        return top != null && top.getSenderName().equals(this.name);
    }

    private synchronized boolean isAllConfirmToAllowEnterCriticalSession() {
        return acquiredLockServerSockets.values().stream().allMatch(allow -> allow);
    }

    private void processCriticalSession(Message message) throws InterruptedException, IOException {
        while(!isLocalWriteRequestFirstInQueue()) {
            System.out.println("Waiting for critical session access...");
            Thread.sleep(100);
        }

        System.out.println("Going into critical session access...");

        var fileName = message.getFileNameFromPayload();
        var lineToAppend = message.getDataFromPayload();

        appendToFile(fileName, lineToAppend);

        // ask all other to append
        var writeSyncMessage = new Message(this.name, Message.MessageType.WriteSyncRequest, localTime, lineToAppend);

        for (var serverSocket : serverSockets.values()) {
            sendMessage(serverSocket, writeSyncMessage.toString());
        }

        // currently assume no failure

        System.out.println("Going out of critical session access...");
    }

    private void appendToFile(String fileName, String message) throws IOException {
        System.out.printf("Appending '%s' to file '%s'\n", message, fileName);

        var filePath = Paths.get(directoryPath, fileName).toAbsolutePath();
        FileWriter fileWriter = new FileWriter(String.valueOf(filePath), true);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println(message);
        printWriter.close();
    }
}
