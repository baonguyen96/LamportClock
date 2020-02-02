import java.util.ArrayList;
import java.util.Arrays;

public class Server {
    public static void main(String[] args){
        var ipsAndPorts = parseIpAddressesAndPorts(args);
        var serverNode = new ServerNode(ipsAndPorts);
    }

    static ArrayList<String> parseIpAddressesAndPorts(String[] args) {
        var ipsAndPorts = new ArrayList<String>();

        if (args.length > 0){
            ipsAndPorts.addAll(Arrays.asList(args));
        }

        return ipsAndPorts;
    }
}
