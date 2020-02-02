import java.util.ArrayList;

public class ServerNode {

    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private int localTime = 0;
    private ArrayList<String> commands;
    private ArrayList<String> serverNodes;


    public ServerNode(ArrayList<String> ipsAndPorts) {

    }

    private void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }



}
