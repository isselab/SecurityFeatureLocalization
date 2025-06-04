package io.github.david0x03.project;

import io.github.david0x03.ParsedFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public abstract class JavaProject {

    protected static final Logger logger = LogManager.getLogger(JavaProject.class);

    private final Path projectPath;
    private final List<JavaSource> sources = new ArrayList<>();

    private List<ParsedFile> parsedFiles = new ArrayList<>();
    protected boolean buildSuccess = false;

    protected String javaSourceVersion = null;
    protected boolean searchedJavaSourceVersion = false;

    protected JavaProject(Path projectPath) {
        this.projectPath = projectPath;
        loadSources();
    }

    /**
     * Loads a Maven or Gradle based Java project
     * Throws an exception when an invalid path or non Maven/Gradle project is loaded
     *
     * @param projectPath Path to the java project
     * @return {@link MavenProject} or {@link GradleProject}
     */
    public static JavaProject load(Path projectPath) throws Exception {
        if (!Files.isDirectory(projectPath))
            throw new Exception("project path must be a valid directory");

        if (MavenProject.isValidProject(projectPath))
            return new MavenProject(projectPath);

        if (GradleProject.isValidProject(projectPath))
            return new GradleProject(projectPath);

        if (AntProject.isValidProject(projectPath))
            return new AntProject(projectPath);

        throw new Exception("project must include a pom.xml or build.gradle file");
    }

    /**
     * Locates the sources from the project
     * Looks for directories with the structure "../src/main/java/.."
     * Skips sources with the term "test" in its path
     */
    private void loadSources() {
        Stream<Path> stream = null;

        try {
            stream = Files.find(this.projectPath, 10, (path, attributes) -> {
                        if (!attributes.isDirectory()) return false;
                        if (!path.endsWith("src/main/java")) return false;

                        // Skip test sources
                        if (path.toAbsolutePath().relativize(getProjectPath()).toString().toLowerCase().contains("test"))
                            return false;

                        return true;
                    }
            );

            stream.forEach(src -> {
                var source = new JavaSource(this, src);
                sources.add(source);
            });
        } catch (IOException e) {
            logger.error("Failed to access the file system: ", e);
        } finally {
            if (stream != null) stream.close();
        }
    }

    /**
     * @return The absolute project path
     */
    public Path getProjectPath() {
        return projectPath.toAbsolutePath();
    }

    /**
     * @return The sources located in the project
     */
    public List<JavaSource> getSources() {
        return sources;
    }

    /**
     * @return The parsed files of the project
     */
    public List<ParsedFile> getParsedFiles() {
        return parsedFiles;
    }

    /**
     * Set the parsed files of the project
     */
    public void setParsedFiles(List<ParsedFile> parsedFiles) {
        this.parsedFiles = parsedFiles;
    }

    /**
     * @return True if the build was successful, False otherwise
     */
    public boolean isBuildSuccess() {
        return buildSuccess;
    }

    protected abstract List<String> getGeneratedFiles(JavaSource source);

    /**
     * Tries to Java identify the used Java source version of the project
     *
     * @return The version as a string e.g. "1.8" or "17", null if the version couldn't be identified
     */
    public abstract String getJavaSourceVersion();

    /**
     * Tries to build the Java Project
     *
     * @return true for a successful build, false if the build fails
     */
    public abstract boolean buildProject();

    protected abstract List<Path> getDependencies(JavaSource source);
}