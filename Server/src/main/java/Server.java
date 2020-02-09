import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class Server {
    public static void main(String[] args) {
        try {
            var otherServers = new ArrayList<ServerInfo>();
            ServerInfo serverInfo = null;
            String directoryPath = null;
            String configurationFile;

            if (args == null || args.length == 0) {
                var scanner = new Scanner(System.in);

                System.out.print("Configuration file (leave blank if not exist): ");
                configurationFile = scanner.nextLine();

                if(configurationFile.trim().isEmpty()) {
                    System.out.print("Directory: ");
                    directoryPath = scanner.nextLine();

                    System.out.print("Name:Ip:Port (separated by colon): ");
                    serverInfo = new ServerInfo(scanner.nextLine());

                    System.out.print("Other servers ((Name:IP:Port) tuples separated by pipe): ");
                    var otherServersInput = scanner.nextLine().split("\\|");

                    if(otherServersInput.length > 0 && !otherServersInput[0].isEmpty()) {
                        for(var input : otherServersInput) {
                            otherServers.add(new ServerInfo(input));
                        }
                    }

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
                serverInfo = new ServerInfo(scanner.nextLine());

                var otherServersInput = scanner.nextLine().split("\\|");
                if(otherServersInput.length > 0 && !otherServersInput[0].isEmpty()) {
                    for(var input : otherServersInput) {
                        otherServers.add(new ServerInfo(input));
                    }
                }
            }

            var serverNode = new ServerNode(serverInfo, otherServers, directoryPath);
            serverNode.up();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
