import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Server {
    public static void main(String[] args){
        try {
            var ipsAndPorts = parseIpAddressesAndPorts(args);

            if(ipsAndPorts.size() < 1){
                ipsAndPorts = getDefaultIpAddressesAndPorts();
            }

            var serverNode = new ServerNode(ipsAndPorts.get(0), new ArrayList<>(ipsAndPorts.stream().skip(1).collect(Collectors.toList())));
            serverNode.up();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    static ArrayList<String> parseIpAddressesAndPorts(String[] args) {
        var ipsAndPorts = new ArrayList<String>();

        if (args.length > 0){
            ipsAndPorts.addAll(Arrays.asList(args));
        }

        return ipsAndPorts;
    }

    static ArrayList<String> getDefaultIpAddressesAndPorts() {
        var ipsAndPorts = new ArrayList<String>();

        ipsAndPorts.add("ip1");
        ipsAndPorts.add("ip2");
        ipsAndPorts.add("ip3");

        return ipsAndPorts;
    }
}
