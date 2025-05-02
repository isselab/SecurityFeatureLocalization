package io.github.david0x03;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents a repository with metadata and methods to clone and delete the repository.
 */
public final class Repository {
    private final long id;
    private final String url;
    private final String owner;
    private final String name;
    private final String createdAt;
    private final int stars;
    private final int size;

    private Path clonedPath = null;

    /**
     * Initializes a Repository instance with metadata.
     *
     * @param id        The unique identifier of the repository.
     * @param url       The URL of the repository.
     * @param owner     The owner of the repository.
     * @param name      The name of the repository.
     * @param createdAt The creation date of the repository.
     * @param stars     The number of stars the repository has.
     * @param size      The size of the repository in KB.
     */
    public Repository(
            long id,
            String url,
            String owner,
            String name,
            String createdAt,
            int stars,
            int size
    ) {
        this.id = id;
        this.url = url;
        this.owner = owner;
        this.name = name;
        this.createdAt = createdAt;
        this.stars = stars;
        this.size = size;
    }

    /**
     * Clones the repository to the specified parent directory using Git.
     *
     * @param parentDir The parent directory where the repository will be cloned.
     * @return The path to the cloned repository.
     * @throws Exception If the cloning process fails.
     */
    public Path cloneRepo(Path parentDir) throws Exception {
        var commands = switch (Utils.getOperatingSystem()) {
            case WINDOWS -> List.of("cmd.exe", "/c", "git", "clone", "--depth", "1", "--single-branch", url);
            case LINUX -> List.of("git", "clone", "--depth", "1", "--single-branch", url);
        };

        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory(parentDir.toFile());

        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            process.waitFor();
            process.destroy();

            clonedPath = parentDir.resolve(name);
            return clonedPath;
        } catch (Exception e) {
            throw new Exception("Failed to clone repository");
        }
    }

    /**
     * Deletes the cloned repository.
     *
     * @throws IOException If the deletion fails.
     */
    public void deleteRepo() throws IOException {
        if (clonedPath != null)
            FileUtils.forceDelete(clonedPath.toFile());
    }

    public long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public int getStars() {
        return stars;
    }

    public int getSize() {
        return size;
    }

    public Path getClonedPath() {
        return clonedPath;
    }
}
