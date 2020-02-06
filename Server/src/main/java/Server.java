import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Server {
    public static void main(String[] args) {
        try {
            String directoryPath;
            String ipPort;
            ArrayList<String> otherServers;
            var scanner = new Scanner(System.in);

            if (args == null || args.length == 0) {
                System.out.print("Directory: ");
                directoryPath = scanner.nextLine();

                System.out.print("Ip and Port (separated by colon): ");
                ipPort = scanner.nextLine();

                System.out.print("Other servers ((IP:Port) tuples separated by space): ");
                var otherServersInput = scanner.nextLine().split(" ");
                otherServers = Arrays.stream(otherServersInput).collect(Collectors.toCollection(ArrayList::new));

                System.out.print("Start server [y/n]: ");
                var confirmation = scanner.nextLine();

                if (!confirmation.toLowerCase().startsWith("y")) {
                    return;
                }
            }
            else {
                directoryPath = args[0];
                ipPort = args[1];
                otherServers = Arrays.stream(args).skip(2).collect(Collectors.toCollection(ArrayList::new));
            }

            var serverNode = new ServerNode(ipPort, otherServers, directoryPath);
            serverNode.up();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
