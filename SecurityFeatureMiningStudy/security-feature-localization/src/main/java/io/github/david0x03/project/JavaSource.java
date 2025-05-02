package io.github.david0x03.project;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.david0x03.Utils.collectFiles;

public class JavaSource {

    private static final Logger logger = LogManager.getLogger(JavaSource.class);

    private final JavaProject project;
    private final Path sourcePath;
    private final List<String> javaFiles = new ArrayList<>();

    /**
     * This object represents a single source of a Java project
     *
     * @param project    The project the source belongs to
     * @param sourcePath The path of the source
     */
    public JavaSource(JavaProject project, Path sourcePath) {
        this.project = project;
        this.sourcePath = sourcePath;

        if (!sourcePath.startsWith(project.getProjectPath())) {
            logger.error("The source is not part of the given project");
            System.exit(1);
        }

        // Find all java files within the source
        collectFiles(sourcePath.toFile(), ".java", javaFiles);
    }

    /**
     * @return The project the source belongs to
     */
    public JavaProject getProject() {
        return project;
    }

    /**
     * @return The absolute path of the source
     */
    public Path getSourcePath() {
        return sourcePath.toAbsolutePath();
    }

    /**
     * @return The relative path of the source to the projects' root
     */
    public Path getRelativeSourcePath() {
        return project.getProjectPath().relativize(sourcePath);
    }

    /**
     * @return The Java files belonging to this source
     */
    public List<String> getJavaFiles() {
        return javaFiles;
    }

    /**
     * @return The generated Java files for the source
     */
    public List<String> getGeneratedFiles() {
        return project.getGeneratedFiles(this);
    }

    /**
     * @return The dependencies files for the source
     */
    public List<Path> getDependencies() {
        return project.getDependencies(this);
    }
}
