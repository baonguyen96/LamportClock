import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerTest {

    @org.junit.jupiter.api.Test
    void testParseIpAddressesAndPortsEmpty() {
        var args = new String[]{};
        var ipAndPort = Server.parseIpAddressesAndPorts(args);
        assertEquals(0, ipAndPort.size());
    }

    @org.junit.jupiter.api.Test
    void testParseIpAddressesAndPortsNotEmpty() {
        var args = new String[]{"1.1.1.1:0000",  "2.2.2.2:1234"};
        var ipAndPort = Server.parseIpAddressesAndPorts(args);
        assertEquals(2, ipAndPort.size());
        assertEquals(args[0], ipAndPort.get(0));
        assertEquals(args[1], ipAndPort.get(1));
    }
}