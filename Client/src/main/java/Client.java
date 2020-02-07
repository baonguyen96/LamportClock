import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Client {
    public static void main(String[] args){
        try {
            String name;
            var servers = new ArrayList<String>();

            if(args == null || args.length == 0) {
                var scanner = new Scanner(System.in);

                System.out.print("Name: ");
                name = scanner.nextLine();

                System.out.print("Servers ((IP:Port) tuples separated by space): ");
                var serversInput = scanner.nextLine().split(" ");
                servers = Arrays.stream(serversInput).collect(Collectors.toCollection(ArrayList::new));

                System.out.print("Start client [y/n]: ");
                var confirmation = scanner.nextLine();

                if (!confirmation.toLowerCase().startsWith("y")) {
                    return;
                }
            }
            else {
                var scanner = new Scanner(new File(args[0]));
                name = scanner.nextLine();
                servers = new ArrayList<>(Arrays.asList(scanner.nextLine().split(" ")));
            }

            var clientNode = new ClientNode(name, servers);
            clientNode.up();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}
