import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Server {
    public static void main(String[] args) {
        try {
            var directoryPath = "Path";
            var ipPort = "localhost:1234";
            var otherServers = new ArrayList<String>();
            String configurationFile;

            if (args == null || args.length == 0) {
                var scanner = new Scanner(System.in);

                System.out.print("Configuration file (leave blank if not exist): ");
                configurationFile = scanner.nextLine();

                if(configurationFile.trim().isEmpty()) {
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
            }
            else {
                configurationFile = args[0];
            }

            if(!configurationFile.isEmpty()) {
                var scanner = new Scanner(new File(configurationFile));
                directoryPath = scanner.nextLine();
                ipPort = scanner.nextLine();
                otherServers = new ArrayList<>(Arrays.asList(scanner.nextLine().split(" ")));
            }

            var serverNode = new ServerNode(ipPort, otherServers, directoryPath);
            serverNode.up();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
