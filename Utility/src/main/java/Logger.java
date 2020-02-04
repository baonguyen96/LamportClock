import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss.SSS z");

    public static void log(String message) {
        var now = new Date(System.currentTimeMillis());
        System.out.print(String.format("%s - %s\n", dateTimeFormat.format(now), message));
    }
}
