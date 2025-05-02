package io.github.david0x03.project;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static io.github.david0x03.Utils.*;

/**
 * Represents a Gradle-based Java project, providing methods to identify the Gradle
 * configuration, extract Java source version, build the project, and manage dependencies.
 */
public class GradleProject extends JavaProject {

    private final boolean hasWrapperExec;

    /**
     * Initializes a Gradle project instance.
     *
     * @param projectPath The path to the project directory.
     */
    public GradleProject(Path projectPath) {
        super(projectPath);

        // Check for a Gradle wrapper executable
        hasWrapperExec = Files.exists(projectPath.resolve("gradlew")) && Files.exists(projectPath.resolve("gradlew.bat"));
    }

    /**
     * Verifies whether the specified path contains a valid Gradle project.
     *
     * @param projectPath The path to the project directory.
     * @return True if the project is a Gradle project, otherwise false.
     */
    public static boolean isValidProject(Path projectPath) {
        return Files.exists(projectPath.resolve("build.gradle"));
    }

    /**
     * Retrieves the Java source version used by the project by executing Gradle commands.
     *
     * @return The Java source version as a string, or null if it cannot be determined.
     */
    @Override
    public String getJavaSourceVersion() {
        if (searchedJavaSourceVersion) return javaSourceVersion;
        searchedJavaSourceVersion = true;

        // Base commands for the Gradle executable
        var commands = getGradleExec();

        // This prints the project properties, including the Java source version if found
        commands.addAll(List.of("properties", "--no-daemon", "--offline"));

        /*
         * Gradle is very sensitive when executed with the wrong Java version and fails quickly
         * However, to identify the Java version, it's the easiest to run the gradle properties command
         * So we try all the installed jdks until we succeed.
         * This is a very hacky workaround, but should be sufficient for the mining study
         */
        var installedJdks = getInstalledJdks();
        if (installedJdks.isEmpty()) {
            logger.error("No jdks installed");
            System.exit(1);
        }

        // No sources found, abort early
        if (getSources().isEmpty()) return null;

        // Try for each installed jdk
        for (var jdk : installedJdks.keySet()) {
            /*
             * Get the build file path
             * - src
             *    - main
             *       - java (srcPath)
             *          ...
             * - build.gradle   <- needed file
             */
            var buildFilePath = getSources().get(0).getSourcePath().getParent().getParent().getParent().resolve("build.gradle");
            if (!Files.isRegularFile(buildFilePath)) continue;

            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            processBuilder.directory(buildFilePath.getParent().toFile());
            processBuilder.environment().put("JAVA_HOME", installedJdks.get(jdk).toString());
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

            try {
                Process process = processBuilder.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Empty the output buffer after the version has been identified
                        if (javaSourceVersion != null) continue;

                        if (line.matches("^sourceCompatibility: (1\\.)?\\d+$")) {
                            String versionStr = line.split(" ")[1];
                            javaSourceVersion = parseJavaVersionString(versionStr);
                        }
                    }

                    process.waitFor();
                    process.destroy();
                }
            } catch (Exception e) {
                logger.error("Failed to execute the Gradle command: ", e);
            }

            // Java version found, don't check any further
            if (javaSourceVersion != null) break;
        }

        return javaSourceVersion;
    }

    /**
     * Builds the project by executing Gradle's `assemble` command.
     *
     * @return True if the build is successful, otherwise false.
     */
    @Override
    public boolean buildProject() {
        var buildFilePath = getProjectPath().resolve("build.gradle");

        // Base commands for the Maven executable
        var commands = getGradleExec();

        // This commands downloads all dependencies locally
        commands.addAll(List.of("assemble", "--gradle-user-home", "./gradle-cache", "--no-daemon"));

        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory(buildFilePath.getParent().toFile());

        // Try to set the found Java version, if it's installed on the system
        // Fall back to 1.8 if no version was found
        var installedJdks = getInstalledJdks();
        var defaultJdk = installedJdks.getOrDefault("1.8", null);
        var jdkVersion = installedJdks.getOrDefault(getJavaSourceVersion(), defaultJdk);

        if (jdkVersion != null)
            processBuilder.environment().put("JAVA_HOME", jdkVersion.toString());

        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            buildSuccess = process.waitFor() == 0;
            process.destroy();
        } catch (Exception e) {
            logger.error("Failed to execute the Gradle command: ", e);
        }

        return buildSuccess;
    }

    /**
     * Retrieves the external dependencies of the project.
     *
     * @param source The Java source file whose dependencies are to be retrieved.
     * @return A list of paths to dependency files.
     */
    @Override
    protected List<Path> getDependencies(JavaSource source) {
        //External dependencies usually are downloaded by maven, but can also be found in /libs or /lib folders
        var depPath = getProjectPath().resolve("gradle-cache/caches/modules-2/files-2.1");
        var libsPath = getProjectPath().resolve("libs");
        var libPath = getProjectPath().resolve("lib");

        var dependencies = new ArrayList<String>();

        if (Files.isDirectory(depPath))
            collectFiles(depPath.toFile(), ".jar", dependencies);

        if (Files.isDirectory(libsPath))
            collectFiles(libsPath.toFile(), ".jar", dependencies);

        if (Files.isDirectory(libPath))
            collectFiles(libPath.toFile(), ".jar", dependencies);

        return dependencies.stream().map(Paths::get).toList();
    }

    /**
     * Retrieves the files generated by Gradle during the build process.
     *
     * @param source The Java source file to check for generated files.
     * @return A list of paths to generated files.
     */
    @Override
    protected List<String> getGeneratedFiles(JavaSource source) {
        var generatedFiles = new ArrayList<String>();
        var generatedSourcesPath = getProjectPath().resolve("build/generated");

        if (!Files.isDirectory(generatedSourcesPath)) return List.of();

        collectFiles(generatedSourcesPath.toFile(), ".java", generatedFiles);
        return generatedFiles;
    }

    /**
     * Determines the commands needed to execute Gradle commands based on the operating system
     * and whether a Gradle wrapper is present.
     *
     * @return A list of command-line arguments for Gradle execution.
     */
    public List<String> getGradleExec() {
        var commands = new ArrayList<String>();

        switch (getOperatingSystem()) {
            case WINDOWS:
                commands.addAll(List.of("cmd.exe", "/c"));
                if (hasWrapperExec) commands.add(getProjectPath().resolve("gradlew.bat").toString());
                else commands.add("gradle");
                break;
            case LINUX:
                if (hasWrapperExec) commands.add(getProjectPath().resolve("gradlew").toString());
                else commands.add("gradle");
                break;
            default:
                logger.error("Unsupported OS");
                System.exit(1);
        }

        return commands;
    }
}