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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ServerNode {
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private int localTime;
    private ServerInfo info;
    private String directoryPath;
    private PriorityQueue<Message> commandsQueue;
    private Hashtable<String, Socket> serverSockets;
    private ArrayList<ServerInfo> otherServers;
    private Logger logger = new Logger(Logger.LogLevel.Release);

    public ServerNode(ServerInfo serverInfo, ArrayList<ServerInfo> otherServerInfos, String directoryPath) throws IOException {
        this.localTime = 0;
        this.info = serverInfo;
        this.directoryPath = directoryPath;
        this.otherServers = otherServerInfos;
        this.serverSockets = new Hashtable<>();
        this.commandsQueue = new PriorityQueue<>();

        FileUtil.truncateAllFilesInDirectory(directoryPath);
    }

    public void up() throws IOException {
        logger.log(String.format("%s starts listening on (%s:%d)...", this.info.getName(), this.info.getIpAddress(), this.info.getPort()));

        var serverSocket = new ServerSocket(this.info.getPort(), 100, InetAddress.getByName(this.info.getIpAddress()));

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
                populateServerSockets();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        linkToOtherServersThread.start();
    }

    private void populateServerSockets() throws InterruptedException {
        if (this.otherServers.isEmpty()) {
            logger.debug("No servers found to connect to");
            return;
        }

        for (var trial = 0; trial < 5; trial++) {
            for (var otherServer : this.otherServers) {
                if (serverSockets.containsKey(otherServer.getName())) {
                    continue;
                }

                logger.debug(String.format("%s tries to connect to %s...", this.info.getName(), otherServer));

                try {
                    var socket = new Socket(otherServer.getIpAddress(), otherServer.getPort());
                    sendMessage(socket, String.format("Server %s", this.info.getName()), true);
                    serverSockets.put(otherServer.getName(), socket);

                    logger.debug(String.format("%s successfully connects to %s", this.info.getName(), otherServer));
                }
                catch (IOException ignored) {
                    logger.debug(String.format("%s fails to connect to %s - attempt %d", this.info.getName(), otherServer, trial + 1));
                }
            }

            if (serverSockets.keySet().size() == otherServers.size()) {
                break;
            }
            else {
                Thread.sleep(500);
            }
        }

        if (serverSockets.size() == 0) {
            logger.debug(String.format("%s cannot connect to any other servers", this.info.getName()));
        }
        else if (serverSockets.size() < otherServers.size()) {
            var successfulServers = String.join(", ", serverSockets.keySet());
            logger.debug(String.format("%s successfully connects to %s server(s): (%s)",
                    this.info.getName(), serverSockets.size(), successfulServers));
        }
        else {
            logger.debug(String.format("%s connect to all server(s)", this.info.getName()));
        }
    }

    private void listenForIncomingMessages(ServerSocket serverSocket) throws IOException {
        Socket incomingSocket;

        while (true) {
            incomingSocket = serverSocket.accept();
            var finalSocket = incomingSocket;

            logger.debug(String.format("%s receives new request from %s", this.info.getName(), incomingSocket));

            if (isServerSocket(finalSocket)) {
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

                logger.log(String.format("%s receives '%s' from %s", this.info.getName(), receivedMessageString, receivedMessage.getSenderName()));

                setLocalTime(receivedMessage.getTimeStamp());
                incrementLocalTime();

                if (receivedMessage.getType() == Message.MessageType.WriteAcquireRequest) {
                    addToQueue(receivedMessage);

                    var responseMessage = new Message(this.info.getName(), Message.MessageType.WriteAcquireResponse, localTime, receivedMessage.getPayload());
                    var serverSocket = serverSockets.get(receivedMessage.getSenderName());
                    sendMessage(serverSocket, responseMessage.toString(), true);
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteAcquireResponse) {
                    addToQueue(receivedMessage);
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteReleaseRequest) {
                    // only remove the WriteAcquireRequest counterpart
                    removeFromQueue(m ->
                            m.getSenderName().equals(receivedMessage.getSenderName()) &&
                                    m.getType() == Message.MessageType.WriteAcquireRequest &&
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

                logger.log(String.format("%s receives '%s' from %s", this.info.getName(), receivedMessageString, receivedMessage.getSenderName()));

                setLocalTime(receivedMessage.getTimeStamp());
                incrementLocalTime();

                var fileName = receivedMessage.getFileNameFromPayload();
                var fullPath = Paths.get(directoryPath, fileName).toAbsolutePath();
                Message responseMessage;

                if (FileUtil.exists(String.valueOf(fullPath))) {
                    var writeAcquireRequest = new Message(this.info.getName(), Message.MessageType.WriteAcquireRequest, localTime, receivedMessage.getPayload());
                    addToQueue(writeAcquireRequest);

                    for (Socket serverSocket : serverSockets.values()) {
                        sendMessage(serverSocket, writeAcquireRequest.toString(), true);
                    }

                    processCriticalSession(writeAcquireRequest);

                    incrementLocalTime();

                    responseMessage = new Message(this.info.getName(), Message.MessageType.WriteSuccessAck, localTime, "");
                }
                else {
                    incrementLocalTime();

                    responseMessage = new Message(this.info.getName(), Message.MessageType.WriteFailureAck, localTime, "");
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

    private void sendMessage(Socket socket, String messageText, boolean toServer) throws IOException {
        logger.log(String.format("%s sends '%s' to %s %s", this.info.getName(), messageText, toServer ? "server" : "client", socket));

        var dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(messageText);
    }

    private void sendMessage(Socket socket, String messageText, String recipientName) throws IOException {
        logger.log(String.format("%s sends '%s' to %s", this.info.getName(), messageText, recipientName));

        var dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(messageText);
    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }

    private synchronized void addToQueue(Message message) {
        logger.debug(String.format("Adding message '%s' to the queue", message.toString()));
        logger.debug("Queue size before add = " + commandsQueue.size());

        commandsQueue.add(message);

        logger.debug("Queue size after add = " + commandsQueue.size());
    }


    private synchronized void removeFromQueue(Predicate<Message> filter) {
        logger.debug("Removing messages off the queue");
        logger.debug("Queue size before remove = " + commandsQueue.size());

        var removingMessages = commandsQueue.stream().filter(filter).collect(Collectors.toList());
        for(var message : removingMessages) {
            logger.debug(String.format("Removing '%s' from the queue", message.toString()));
        }

        commandsQueue.removeAll(removingMessages);

        logger.debug("Queue size after remove = " + commandsQueue.size());
    }

    private boolean isMessageFirstInQueue(Message message) {
        if (commandsQueue.isEmpty()) {
            return true;
        }

        var top = commandsQueue.peek();

        logger.debug("Top of queue = " + top.toString());
        logger.debug("Current message = " + message.toString());

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

        logger.debug("All senders after request = " + String.join(", ", allSendersAfterWriteRequest));

        return allSendersAfterWriteRequest.length >= serverSockets.size();
    }

    private void processCriticalSession(Message writeAcquireRequest) throws InterruptedException, IOException {
        logger.debug(String.format("Checking allowance to proceed to critical session for message '%s'...", writeAcquireRequest.toString()));

        while (!isMessageFirstInQueue(writeAcquireRequest) || !isAllConfirmToAllowEnterCriticalSession(writeAcquireRequest)) {
            logger.debug("Waiting for critical session access...");
            Thread.sleep(100);
        }

        logger.debug("Going into critical session...");

        var fileName = writeAcquireRequest.getFileNameFromPayload();
        var lineToAppend = writeAcquireRequest.getDataFromPayload();

        appendToFile(fileName, lineToAppend);

        incrementLocalTime();

        var writeSyncRequest = new Message(this.info.getName(), Message.MessageType.WriteSyncRequest, localTime, writeAcquireRequest.getPayload());

        for (var serverSocket : serverSockets.values()) {
            sendMessage(serverSocket, writeSyncRequest.toString(), true);
        }

        // since current writeSyncRequest must be the highest timestamped message in the queue for the current payload,
        // therefore can remove any message for this payload with lesser timestamp
        removeFromQueue(m -> m.compareTo(writeSyncRequest) < 0 && m.getPayload().equals(writeSyncRequest.getPayload()));

        incrementLocalTime();

        // notify all others to release mutex
        var writeReleaseRequest = new Message(this.info.getName(), Message.MessageType.WriteReleaseRequest, localTime, "");

        for (Socket serverSocket : serverSockets.values()) {
            sendMessage(serverSocket, writeReleaseRequest.toString(), true);
        }

        incrementLocalTime();

        logger.debug("Going out of critical session access");
    }

    private synchronized void appendToFile(String fileName, String message) throws IOException {
        var filePath = Paths.get(directoryPath, fileName).toAbsolutePath();

        logger.log(String.format("%s appends '%s' to file %s", this.info.getName(), message, fileName));

        FileUtil.appendToFile(String.valueOf(filePath), message);
    }
}
