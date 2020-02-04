import java.io.*;
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
    private String name;
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private PriorityQueue<Message> commandsQueue;
    private String directoryPath;


    public ServerNode(String name, ArrayList<String> ipsAndPorts, String directoryPath) {
        localTime = 0;
        this.name = name;
        serverNodes = ipsAndPorts;
        serverSockets = new Hashtable<>();
        commandsQueue = new PriorityQueue<>();
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

            Logger.log("New request received : " + incomingSocket);

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
                var receivedMessage = new Message(receivedMessageString);

                Logger.log(String.format("Receiving '%s' from (%s:%d)\n", receivedMessageString, socket.getInetAddress(), socket.getPort()));

                setLocalTime(receivedMessage.getTimeStamp());
                incrementLocalTime();

                if (receivedMessage.getType() == Message.MessageType.WriteAcquireRequest) {
                    commandsQueue.add(receivedMessage);

                    var responseMessage = new Message(this.name, Message.MessageType.WriteAcquireResponse, localTime, "");
                    sendMessage(socket, responseMessage.toString());
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteReleaseRequest) {
                    // assume a server only triggers a single write acquire request at a time
                    // since if it wants to monopolize the critical session it can just prolong its occupation on the session instead
                    commandsQueue.removeIf(m -> m.getType() == Message.MessageType.WriteAcquireRequest && m.getSenderName().equals(receivedMessage.getSenderName()));
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteSyncRequest) {
                    // append to file directly since this message type can only occur when 1 and only 1 server process in critical session
                    var fileName = receivedMessage.getFileNameFromPayload();
                    var lineToAppend = receivedMessage.getDataFromPayload();
                    appendToFile(fileName, lineToAppend);
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteAcquireResponse) {
                    commandsQueue.add(receivedMessage);
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
                var receivedMessage = new Message(receivedMessageString);

                Logger.log(String.format("Receiving '%s' from (%s:%d)\n", receivedMessageString, socket.getInetAddress(), socket.getPort()));

                setLocalTime(receivedMessage.getTimeStamp());
                incrementLocalTime();

                var writeRequestMessage = new Message(this.name, Message.MessageType.WriteAcquireRequest, localTime, receivedMessage.getPayload());
                commandsQueue.add(writeRequestMessage);

                for (var serverName : serverSockets.keySet()) {
                    var serverSocket = serverSockets.get(serverName);
                    sendMessage(serverSocket, writeRequestMessage.toString());
                }

                var writeRequestedTime = localTime;

                // busy wait until all confirmed to be proceeding
                while (!isAllConfirmToAllowEnterCriticalSession(writeRequestedTime)) {
                    Thread.sleep(100);
                }

                processCriticalSession(writeRequestMessage);

                var writeReleaseMessage = new Message(this.name, Message.MessageType.WriteReleaseRequest, localTime, "");
                for (var serverName : serverSockets.keySet()) {
                    var serverSocket = serverSockets.get(serverName);
                    sendMessage(serverSocket, writeReleaseMessage.toString());
                }

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
        Logger.log(String.format("Sending '%s' to '(%s:%d)...'\n", message, socket.getInetAddress(), socket.getPort()));

        var dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(message);
    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }

    private synchronized boolean isLocalWriteRequestFirstInQueue(Message message) {
        var top = commandsQueue.peek();
        return top != null && top.getSenderName().equals(this.name);
    }

    // this is expensive if a lot of messages waiting in the queue
    private synchronized boolean isAllConfirmToAllowEnterCriticalSession(int writeRequestedTime) {
        var allSendersAfterWriteRequest = commandsQueue
                .stream()
                .filter(message -> message.getTimeStamp() > writeRequestedTime)
                .map(Message::getSenderName)
                .distinct();

        return allSendersAfterWriteRequest.count() == serverSockets.size();
    }

    private synchronized void processCriticalSession(Message writeRequestMessage) throws InterruptedException, IOException {

        // enter critical session if my write request is the first in the queue
        while (!isLocalWriteRequestFirstInQueue(writeRequestMessage)) {
            Logger.log("Waiting for critical session access...");
            Thread.sleep(100);
        }

        Logger.log("Going into critical session...");

        var fileName = writeRequestMessage.getFileNameFromPayload();
        var lineToAppend = writeRequestMessage.getDataFromPayload();
        appendToFile(fileName, lineToAppend);

        var writeSyncRequest = new Message(this.name, Message.MessageType.WriteSyncRequest, localTime, writeRequestMessage.getPayload());
        for (var serverSocket : serverSockets.values()) {
            sendMessage(serverSocket, writeSyncRequest.toString());
        }

        // currently assume no failure

        Logger.log("Going out of critical session access...");
    }

    private synchronized void appendToFile(String fileName, String message) throws IOException {
        Logger.log(String.format("Appending '%s' to file '%s'\n", message, fileName));

        var filePath = Paths.get(directoryPath, fileName).toAbsolutePath();
        FileWriter fileWriter = new FileWriter(String.valueOf(filePath), true);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println(message);
        printWriter.close();
    }
}
