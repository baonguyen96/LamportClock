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
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private int localTime;
    private String name;
    private String directoryPath;
    private PriorityQueue<Message> commandsQueue;
    private Hashtable<String, Socket> serverSockets;
    private ArrayList<String> otherServers;

    public ServerNode(String name, ArrayList<String> otherServers, String directoryPath) throws IOException {
        this.localTime = 0;
        this.name = name;
        this.directoryPath = directoryPath;
        this.otherServers = otherServers;
        this.serverSockets = new Hashtable<>();
        this.commandsQueue = new PriorityQueue<>();

        Logger.log(String.format("Clean up directory '%s'", directoryPath));
        FileUtil.truncateAllFilesInDirectory(directoryPath);
    }

    public void up() {
        var listenThread = new Thread(() -> {
            try {
                listenForIncomingMessages();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        });
        listenThread.start();

        var linkToOtherServersThread = new Thread(() -> {
            try {
                populateServerSockets(otherServers);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        linkToOtherServersThread.start();
    }

    private void populateServerSockets(ArrayList<String> servers) throws InterruptedException {
        if (servers == null || servers.size() == 0 || (servers.size() == 1 && servers.get(0).isEmpty())) {
            Logger.log("No servers found to connect to");
            return;
        }

        for (var trial = 0; trial < 5; trial++) {
            for (var server : servers) {
                if (serverSockets.containsKey(server)) {
                    continue;
                }

                Logger.log(String.format("Trying to connect to (%s)...", server));

                try {
                    var tokenizer = new StringTokenizer(server, ":");
                    var serverIp = InetAddress.getByName(tokenizer.nextToken());
                    var serverPort = Integer.parseInt(tokenizer.nextToken());
                    var socket = new Socket(serverIp, serverPort);
                    sendMessage(socket, String.format("Server %s", this.name));
                    serverSockets.put(server, socket);

                    Logger.log(String.format("Successfully connected to (%s)", server));
                }
                catch (IOException ignored) {
                    Logger.log(String.format("Error trying to connect to (%s) - attempt %d", server, trial + 1));
                }
            }

            if (serverSockets.keySet().size() == servers.size()) {
                break;
            }
            else {
                Thread.sleep(500);
            }
        }

        if (serverSockets.size() == 0) {
            Logger.log("Cannot connect to any other servers");
        }
        else if (serverSockets.size() < servers.size()) {
            var successfulServers = String.join(", ", serverSockets.keySet());
            Logger.log(String.format("Successfully connect to %s server(s): (%s)", serverSockets.size(), successfulServers));
        }
        else {
            Logger.log("Successfully connect to all server(s)");
        }
    }

    private void listenForIncomingMessages() throws IOException {
        var tokenizer = new StringTokenizer(this.name, ":");
        var ipAddress = InetAddress.getByName(tokenizer.nextToken());
        var portNumber = Integer.parseInt(tokenizer.nextToken());
        var serverSocket = new ServerSocket(portNumber, 100, ipAddress);
        Socket incomingSocket;

        Logger.log(String.format("Starting to listen on (%s)...", this.name));

        while (true) {
            incomingSocket = serverSocket.accept();
            var finalSocket = incomingSocket;
            var name = String.format("%s:%s", incomingSocket.getInetAddress(), incomingSocket.getPort());

            Logger.log("New request received : " + incomingSocket);

            if (isServerSocket(finalSocket)) {
                Logger.log(String.format("Handling server communication with (%s)", name));

                var thread = new Thread(() -> {
                    try {
                        handleServerServerCommunication(finalSocket);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                thread.start();
            }
            else {
                Logger.log(String.format("Handling client communication with (%s)", name));

                var thread = new Thread(() -> {
                    try {
                        handleClientServerCommunication(finalSocket);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                thread.start();
            }
        }
    }

    private void handleServerServerCommunication(Socket socket) throws IOException {
        var communicationOn = true;
        var dis = new DataInputStream(socket.getInputStream());

        while (communicationOn) {
            try {
                var receivedMessageString = dis.readUTF();
                var receivedMessage = new Message(receivedMessageString);

                Logger.log(String.format("Receiving '%s' from server (%s:%d)", receivedMessageString, socket.getInetAddress(), socket.getPort()));

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
                e.printStackTrace();
            }
        }

        dis.close();
    }

    private void handleClientServerCommunication(Socket socket) throws IOException {
        var communicationOn = true;
        var dis = new DataInputStream(socket.getInputStream());

        while (communicationOn) {
            try {
                var receivedMessageString = dis.readUTF();
                var receivedMessage = new Message(receivedMessageString);

                Logger.log(String.format("Receiving '%s' from client (%s:%d)", receivedMessageString, socket.getInetAddress(), socket.getPort()));

                setLocalTime(receivedMessage.getTimeStamp());
                incrementLocalTime();

                var fileName = receivedMessage.getFileNameFromPayload();
                var fullPath = Paths.get(directoryPath, fileName).toAbsolutePath();
                Message responseMessage;

                if (FileUtil.exists(String.valueOf(fullPath))) {
                    var writeAcquireRequest = new Message(this.name, Message.MessageType.WriteAcquireRequest, localTime, receivedMessage.getPayload());
                    commandsQueue.add(writeAcquireRequest);

                    for (Socket serverSocket : serverSockets.values()) {
                        sendMessage(serverSocket, writeAcquireRequest.toString());
                    }

                    processCriticalSession(writeAcquireRequest);

                    incrementLocalTime();

                    var writeReleaseRequest = new Message(this.name, Message.MessageType.WriteReleaseRequest, localTime, "");

                    for (Socket serverSocket : serverSockets.values()) {
                        sendMessage(serverSocket, writeReleaseRequest.toString());
                    }

                    incrementLocalTime();

                    responseMessage = new Message(this.name, Message.MessageType.WriteCompleteResponse, localTime, "");
                }
                else {
                    incrementLocalTime();

                    responseMessage = new Message(this.name, Message.MessageType.WriteFailureResponse, localTime, "");
                }

                sendMessage(socket, responseMessage.toString());
            }
            catch (Exception e) {
                communicationOn = false;
            }
        }

        dis.close();
    }

    private boolean isServerSocket(Socket socket) throws IOException {
        var dis = new DataInputStream(socket.getInputStream());
        var socketType = dis.readUTF();

        return socketType.toLowerCase().startsWith("server");
    }

    private void sendMessage(Socket socket, String message) throws IOException {
        Logger.log(String.format("Sending '%s' to %s (%s:%d)",
                message, (serverSockets.containsValue(socket)) ? "server" : "client",
                socket.getInetAddress(), socket.getPort()));

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

    private synchronized void processCriticalSession(Message writeAcquireRequest) throws InterruptedException, IOException {
        Logger.log("Checking allowance to proceed to critical session...");

        while (!isLocalWriteRequestFirstInQueue(writeAcquireRequest)) {
            Logger.log("Waiting for critical session access...");
            Thread.sleep(100);
        }

        Logger.log("Going into critical session...");

        var fileName = writeAcquireRequest.getFileNameFromPayload();
        var lineToAppend = writeAcquireRequest.getDataFromPayload();

        appendToFile(fileName, lineToAppend);

        var writeSyncRequest = new Message(this.name, Message.MessageType.WriteSyncRequest, localTime, writeAcquireRequest.getPayload());
        for (var serverSocket : serverSockets.values()) {
            sendMessage(serverSocket, writeSyncRequest.toString());
        }

        commandsQueue.remove(writeAcquireRequest);

        Logger.log("Going out of critical session access");
    }

    private synchronized void appendToFile(String fileName, String message) throws IOException {
        var filePath = Paths.get(directoryPath, fileName).toAbsolutePath();

        Logger.log(String.format("Appending '%s' to file '%s'", message, filePath));

        FileUtil.appendToFile(String.valueOf(filePath), message);
    }
}
