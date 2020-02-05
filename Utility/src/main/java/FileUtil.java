import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
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
}
