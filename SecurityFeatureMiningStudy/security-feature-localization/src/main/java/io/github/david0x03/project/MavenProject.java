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
 * Represents a Maven-based Java project, providing methods to identify the Gradle
 * configuration, extract Java source version, build the project, and manage dependencies.
 */
public class MavenProject extends JavaProject {

    private final boolean hasWrapperExec;

    /**
     * Initializes a Maven project instance.
     *
     * @param projectPath The path to the project directory.
     */
    public MavenProject(Path projectPath) {
        super(projectPath);

        // Check for a Maven wrapper executable
        hasWrapperExec = Files.exists(projectPath.resolve("mvnw")) && Files.exists(projectPath.resolve("mvnw.cmd"));
    }

    /**
     * Verifies whether the specified path contains a valid Maven project.
     *
     * @param projectPath The path to the project directory.
     * @return True if the project is a Maven project, otherwise false.
     */
    public static boolean isValidProject(Path projectPath) {
        return Files.exists(projectPath.resolve("pom.xml"));
    }

    /**
     * Retrieves the Java source version used by the project by executing Maven commands.
     *
     * @return The Java source version as a string, or null if it cannot be determined.
     */
    @Override
    public String getJavaSourceVersion() {
        if (searchedJavaSourceVersion) return javaSourceVersion;
        searchedJavaSourceVersion = true;

        var pomFilePath = getProjectPath().resolve("pom.xml");

        // Base commands for the Maven executable
        var commands = getMavenExec();

        // This builds the effective pom, listing the Java source version as a property if found
        commands.add("help:effective-pom");

        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory(pomFilePath.getParent().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    // Empty the output buffer after the version has been identified
                    if (javaSourceVersion != null) continue;
                    String trimmedLine = line.trim();

                    if (trimmedLine.matches("<source>(1\\.)?\\d+</source>")) {
                        javaSourceVersion = parseJavaVersionString(trimmedLine.replaceAll("</?source>", ""));
                    }

                    if (trimmedLine.matches("<release>(1\\.)?\\d+</release>")) {
                        javaSourceVersion = parseJavaVersionString(trimmedLine.replaceAll("</?release>", ""));
                    }

                    if (trimmedLine.matches("<maven.compiler.source>(1\\.)?\\d+</maven.compiler.source>")) {
                        javaSourceVersion = parseJavaVersionString(trimmedLine.replaceAll("</?maven.compiler.source>", ""));
                    }
                }

                process.waitFor();
                process.destroy();
            }
        } catch (Exception e) {
            logger.error("Failed to execute the Maven command: ", e);
        }

        return javaSourceVersion;
    }

    /**
     * Builds the Mavens project.
     *
     * @return True if the build is successful, otherwise false.
     */
    @Override
    public boolean buildProject() {
        var pomFilePath = getProjectPath().resolve("pom.xml");

        // Base commands for the Maven executable
        var commands = getMavenExec();

        // This commands downloads all dependencies locally
        commands.addAll(List.of("dependency:copy-dependencies", "-fae"));

        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory(pomFilePath.getParent().toFile());

        // Try to set the found Java version, if it's installed on the system
        // Fall back to 1.8 if no version was found
        var installedJdks = getInstalledJdks();
        var defaultJdk = installedJdks.getOrDefault("1.8", null);
        var jdkVersion = installedJdks.getOrDefault(getJavaSourceVersion(), defaultJdk);

        if (jdkVersion != null)
            processBuilder.environment().put("JAVA_HOME", jdkVersion.toString());

        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            buildSuccess = process.waitFor() == 0;
            process.destroy();
        } catch (Exception e) {
            logger.error("Failed to execute the Maven command: ", e);
        }

        return buildSuccess;
    }

    /**
     * Retrieves the external dependencies of the project.
     *
     * @param source The Java source file whose dependencies are to be retrieved.
     * @return A list of paths to dependency files.
     */
    protected List<Path> getDependencies(JavaSource source) {
        //External dependencies usually are downloaded by maven, but can also be found in /libs or /lib folders
        var mvnDependencies = getDependenciesRecursively(source.getSourcePath());
        var libsPath = getProjectPath().resolve("libs");
        var libPath = getProjectPath().resolve("lib");

        var dependencies = new ArrayList<String>();
        dependencies.addAll(mvnDependencies);

        if (Files.isDirectory(libsPath))
            collectFiles(libsPath.toFile(), ".jar", dependencies);

        if (Files.isDirectory(libPath))
            collectFiles(libPath.toFile(), ".jar", dependencies);

        // Turn the string paths into Path objects
        return dependencies.stream().map(Paths::get).toList();
    }

    /**
     * Recursively retrieves dependencies for Maven projects with submodules.
     * Dependencies are often scattered across parent modules, so this method
     * traverses upward through the module hierarchy to collect them.
     *
     * @param path The path to the current module directory.
     * @return A list of dependency file paths in the module hierarchy.
     */
    private List<String> getDependenciesRecursively(Path path) {
        if (!Files.isDirectory(path)) return List.of();

        var dependencies = new ArrayList<String>();
        var depPath = path.resolve("target/dependency");

        if (Files.isDirectory(depPath))
            collectFiles(depPath.toFile(), ".jar", dependencies);

        if (path.equals(getProjectPath())) return dependencies;

        dependencies.addAll(getDependenciesRecursively(path.getParent()));
        return dependencies;
    }

    /**
     * Retrieves the files generated by Maven during the build process.
     *
     * @param source The Java source file to check for generated files.
     * @return A list of paths to generated files.
     */
    @Override
    protected List<String> getGeneratedFiles(JavaSource source) {
        return getGeneratedFilesRecursively(source.getSourcePath());
    }

    /**
     * Recursively retrieves generated Java files for Maven projects with submodules.
     * Generated files are often located in the `target/generated-sources` directory of
     * each module. This method traverses upward through the module hierarchy to collect them.
     *
     * @param path The path to the current module directory.
     * @return A list of generated Java file paths in the module hierarchy.
     */
    private List<String> getGeneratedFilesRecursively(Path path) {
        if (!Files.isDirectory(path)) return List.of();

        var generatedFiles = new ArrayList<String>();
        var generatedSourcesPath = path.resolve("target/generated-sources");

        if (Files.isDirectory(generatedSourcesPath))
            collectFiles(generatedSourcesPath.toFile(), ".java", generatedFiles);

        if (path.equals(getProjectPath())) return generatedFiles;

        generatedFiles.addAll(getGeneratedFilesRecursively(path.getParent()));
        return generatedFiles;
    }

    /**
     * Determines the commands needed to execute Maven commands based on the operating system
     * and whether a Maven wrapper is present.
     *
     * @return A list of command-line arguments for Maven execution.
     */
    public List<String> getMavenExec() {
        var commands = new ArrayList<String>();

        switch (getOperatingSystem()) {
            case WINDOWS:
                commands.addAll(List.of("cmd.exe", "/c"));
                if (hasWrapperExec) commands.add(getProjectPath().resolve("mvnw.cmd").toString());
                else commands.add("mvn");
                break;
            case LINUX:
                if (hasWrapperExec) commands.add(getProjectPath().resolve("mvnw").toString());
                else commands.add("mvn");
                break;
            default:
                logger.error("Unsupported OS");
                System.exit(1);
        }

        return commands;
    }

}
