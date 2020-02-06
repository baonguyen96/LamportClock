import java.io.*;
import java.nio.file.Paths;

public class FileUtil {
    public static void truncateAllFilesInDirectory(String directoryPath) throws FileNotFoundException {
        var directory = new File(directoryPath);
        var files = directory.list();

        if (files != null) {
            for (var file : files) {
                truncateFile(Paths.get(directoryPath, file).toString());
            }
        }
    }

    public static void truncateFile(String fileName) throws FileNotFoundException {
        var writer = new PrintWriter(fileName);
        writer.print("");
        writer.close();
    }

    public static void appendToFile(String fileName, String line) throws IOException {
        var fileWriter = new FileWriter(fileName, true);
        var printWriter = new PrintWriter(fileWriter);
        printWriter.println(line);
        printWriter.close();
    }

    public static boolean exists(String fileName) {
        var file = new File(fileName);
        return file.exists();
    }
}
