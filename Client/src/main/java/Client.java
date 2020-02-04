import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Client {
    public static void main(String[] args){
        try {
            String name;
            ArrayList<String> servers;

            if(args == null || args.length == 0) {
                var scanner = new Scanner(System.in);

                System.out.print("Name: ");
                name = scanner.nextLine();

                System.out.print("Servers ((IP:Port) tuples separated by space): ");
                var serversInput = scanner.nextLine().split(" ");
                servers = Arrays.stream(serversInput).collect(Collectors.toCollection(ArrayList::new));
            }
            else {
                name = args[0];
                servers = Arrays.stream(args).skip(1).collect(Collectors.toCollection(ArrayList::new));
            }

            var clientNode = new ClientNode(name, servers);
            clientNode.up();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}
