import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Client {
    public static void main(String[] args){
        try {
            ArrayList<String> servers;

            if(args == null || args.length == 0) {
                System.out.print("Servers ((IP:Port) tuples separated by space): ");

                var scanner = new Scanner(System.in);
                var otherServersInput = scanner.nextLine().split(" ");
                servers = Arrays.stream(otherServersInput).collect(Collectors.toCollection(ArrayList::new));
            }
            else {
                servers = Arrays.stream(args).collect(Collectors.toCollection(ArrayList::new));
            }

            var clientNode = new ClientNode(servers);
            clientNode.up();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}
