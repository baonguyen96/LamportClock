import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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

    public void up() throws IOException {
        Logger.log(String.format("Starting to listen on (%s)...", this.name));

        var tokenizer = new StringTokenizer(this.name, ":");
        var ipAddress = InetAddress.getByName(tokenizer.nextToken());
        var portNumber = Integer.parseInt(tokenizer.nextToken());
        var serverSocket = new ServerSocket(portNumber, 100, ipAddress);

        var listenThread = new Thread(() -> {
            try {
                listenForIncomingMessages(serverSocket);
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
            for (var serverName : servers) {
                if (serverSockets.containsKey(serverName)) {
                    continue;
                }

                Logger.log(String.format("Trying to connect to (%s)...", serverName));

                try {
                    var tokenizer = new StringTokenizer(serverName, ":");
                    var serverIp = InetAddress.getByName(tokenizer.nextToken());
                    var serverPort = Integer.parseInt(tokenizer.nextToken());
                    var socket = new Socket(serverIp, serverPort);
                    sendMessage(socket, String.format("Server %s", this.name), true);
                    serverSockets.put(serverName, socket);

                    Logger.log(String.format("Successfully connected to (%s)", serverName));
                }
                catch (IOException ignored) {
                    Logger.log(String.format("Error trying to connect to (%s) - attempt %d", serverName, trial + 1));
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

    private void listenForIncomingMessages(ServerSocket serverSocket) throws IOException {
        Socket incomingSocket;

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
                    var serverSocket = serverSockets.get(receivedMessage.getSenderName());
                    sendMessage(serverSocket, responseMessage.toString(), true);
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteAcquireResponse) {
                    commandsQueue.add(receivedMessage);
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteReleaseRequest) {
                    // assume a server only triggers a single write acquire request at a time
                    // since if it wants to monopolize the critical session it can just prolong its occupation on the session instead
                    commandsQueue.removeIf(m ->
                            m.getSenderName().equals(receivedMessage.getSenderName()) &&
                            m.getTimeStamp() < receivedMessage.getTimeStamp());
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteSyncRequest) {
                    // append to file directly since this message type can only occur when 1 and only 1 server process in critical session
                    var fileName = receivedMessage.getFileNameFromPayload();
                    var lineToAppend = receivedMessage.getDataFromPayload();
                    appendToFile(fileName, lineToAppend);
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
                        sendMessage(serverSocket, writeAcquireRequest.toString(), true);
                    }

                    processCriticalSession(writeAcquireRequest);

                    incrementLocalTime();

                    responseMessage = new Message(this.name, Message.MessageType.WriteCompleteResponse, localTime, "");
                }
                else {
                    incrementLocalTime();

                    responseMessage = new Message(this.name, Message.MessageType.WriteFailureResponse, localTime, "");
                }

                sendMessage(socket, responseMessage.toString(), false);
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

    private void sendMessage(Socket socket, String message, boolean toServer) throws IOException {
        Logger.log(String.format("Sending '%s' to %s (%s:%d:%d)",
                message, toServer ? "server" : "client",
                socket.getInetAddress(), socket.getPort(), socket.getLocalPort()));

        var dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(message);
    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }

    private synchronized boolean isMessageFirstInQueue(Message message) {
        if (commandsQueue.isEmpty()) {
            return true;
        }

        var top = commandsQueue.peek();

        return top.getSenderName().equals(message.getSenderName()) &&
                top.getTimeStamp() == message.getTimeStamp();
    }

    private boolean isAllConfirmToAllowEnterCriticalSession(Message writeAcquireRequest) {
        var allSendersAfterWriteRequest = commandsQueue
                .stream()
                .filter(message -> message.getTimeStamp() > writeAcquireRequest.getTimeStamp())
                .map(Message::getSenderName)
                .distinct()
                .toArray(String[]::new);

        System.out.println("All sender after write acquire request = " + String.join(", ", allSendersAfterWriteRequest));

        return allSendersAfterWriteRequest.length == serverSockets.size();
    }

    private void processCriticalSession(Message writeAcquireRequest) throws InterruptedException, IOException {
        Logger.log("Checking allowance to proceed to critical session...");

        while (!isMessageFirstInQueue(writeAcquireRequest) || !isAllConfirmToAllowEnterCriticalSession(writeAcquireRequest)) {
            Logger.log("Waiting for critical session access...");
            Thread.sleep(100);
        }

        Logger.log("Going into critical session...");

        var fileName = writeAcquireRequest.getFileNameFromPayload();
        var lineToAppend = writeAcquireRequest.getDataFromPayload();

        appendToFile(fileName, lineToAppend);

        incrementLocalTime();

        var writeSyncRequest = new Message(this.name, Message.MessageType.WriteSyncRequest, localTime, writeAcquireRequest.getPayload());

        for (var serverSocket : serverSockets.values()) {
            sendMessage(serverSocket, writeSyncRequest.toString(), true);
        }

        // since current writeSyncRequest must be the highest timestamped message in the queue, can remove anything less than that
        commandsQueue.removeIf(m -> m.compareTo(writeSyncRequest) < 0);

        incrementLocalTime();

        // notify all others to release mutex
        var writeReleaseRequest = new Message(this.name, Message.MessageType.WriteReleaseRequest, localTime, "");

        for (Socket serverSocket : serverSockets.values()) {
            sendMessage(serverSocket, writeReleaseRequest.toString(), true);
        }

        Logger.log("Going out of critical session access");
    }

    private synchronized void appendToFile(String fileName, String message) throws IOException {
        var filePath = Paths.get(directoryPath, fileName).toAbsolutePath();

        Logger.log(String.format("Appending '%s' to file '%s'", message, filePath));

        FileUtil.appendToFile(String.valueOf(filePath), message);
    }
}
