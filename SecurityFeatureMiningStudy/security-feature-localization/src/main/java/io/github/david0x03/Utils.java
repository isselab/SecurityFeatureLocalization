package io.github.david0x03;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides utility functions for file collection, OS detection, Java version parsing, and locating installed JDKs.
 */
public class Utils {

    private static final Logger logger = LogManager.getLogger(Utils.class);

    /**
     * Recursively collects all files with the specified file extension starting from a root directory.
     *
     * @param dir        The root directory to start the search.
     * @param fileEnding The file extension to filter files (e.g., ".java").
     * @param files      A list to store the paths of the collected files.
     */
    public static void collectFiles(File dir, String fileEnding, List<String> files) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) // Recursion
                collectFiles(file, fileEnding, files);
            else if (file.isFile() && file.getName().endsWith(fileEnding))
                files.add(file.getAbsolutePath());
        }
    }

    /**
     * Detects the operating system of the current environment.
     *
     * @return An {@link OS} enum representing the detected operating system.
     */
    public static OS getOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) return OS.WINDOWS;
        if (osName.contains("linux")) return OS.LINUX;

        throw new RuntimeException("The operating system is not supported.");
    }

    /**
     * Parses a Java version string into a unified format.
     * For example, "8" is converted to "1.8".
     *
     * @param version The raw Java version string.
     * @return The parsed Java version string in a standard format.
     */
    public static String parseJavaVersionString(String version) {
        if (version.matches("\\d+") && Integer.parseInt(version) <= 8)
            return "1." + version;
        else return version;
    }

    /**
     * Locates all installed JDKs in the JAVA_HOME directory's parent folder.
     * The JDK directories are expected to follow the naming convention 'jdk-[version]'.
     *
     * @return A map where the keys are Java versions and the values are paths to the JDK directories.
     */
    public static Map<String, Path> getInstalledJdks() {
        var javaJdkDir = Paths.get(System.getenv("JAVA_HOME")).getParent();
        if (!Files.isDirectory(javaJdkDir)) return Map.of();

        try (var dirs = Files.list(javaJdkDir)) {
            var jdkDirs = dirs.filter(Files::isDirectory).toList();

            return jdkDirs.stream().filter(path -> path.getFileName().toString().matches("jdk-\\d+(\\.\\d+)?")).collect(Collectors.toMap(
                    path -> path.getFileName().toString().replace("jdk-", ""),
                    path -> path
            ));
        } catch (IOException e) {
            logger.error("Failed to access the file system: ", e);
        }

        return Map.of();
    }

    public enum OS {WINDOWS, LINUX}
}
