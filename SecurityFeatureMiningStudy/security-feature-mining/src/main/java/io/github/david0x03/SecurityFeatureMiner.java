package io.github.david0x03;

import io.github.david0x03.metrics.LocCalculator;
import io.github.david0x03.project.JavaProject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.*;

import static io.github.david0x03.Database.addFailedRepoMining;
import static io.github.david0x03.Database.addMinedRepo;

/**
 * Mines security features from Java repositories by cloning, analyzing, and extracting metrics.
 * Handles Android project detection, feature extraction, and repository cleanup.
 */
public class SecurityFeatureMiner {

    private static final Logger logger = LogManager.getLogger(SecurityFeatureMiner.class);

    private final Path clonePath;
    private final Path mappingPath;

    /**
     * Initializes the SecurityFeatureMiner with directories for cloning repositories and loading mappings.
     */
    public SecurityFeatureMiner() {
        var cwdPath = Paths.get("");
        clonePath = cwdPath.resolve("clone");
        mappingPath = cwdPath.resolve("lib-mappings");

        try {
            FileUtils.forceMkdir(clonePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Mines repositories from the database up to the specified limit. For each repository:
     *
     * @param limit The maximum number of repositories to mine. Use -1 for no limit.
     */
    public void mine(int limit) {
        int minedRepos = 0;

        if (!Files.isDirectory(mappingPath)) {
            logger.error("No mapping dir found");
            return;
        }

        while (limit == -1 || minedRepos < limit) {
            var repo = Database.getRepository();

            if (repo == null) break;

            Path repoPath = null;
            try {
                logger.info("Cloning: " + repo.getUrl());
                repoPath = repo.cloneRepo(clonePath);
                logger.info("Done");
            } catch (Exception e) {
                addFailedRepoMining(repo, "cloning failed");
                logger.error("Cloning failed: ", e);
                continue;
            }

            if (isAndroidProject(repoPath)) {
                logger.info("Android project detected, skipping...");
                addFailedRepoMining(repo, "android project");
                deleteRepo(repo);
                continue;
            }

            JavaProject project = null;
            try {
                logger.info("Extracting features...");
                project = analyzeRepo(repoPath);
                minedRepos++;
                logger.info("Done");
            } catch (Exception e) {
                logger.error("Feature extraction failed: ", e);
                addFailedRepoMining(repo, "feature extraction failed");
                deleteRepo(repo);
                continue;
            }

            if (project == null) {
                logger.error("Feature extraction failed");
                addFailedRepoMining(repo, "feature extraction failed");
                deleteRepo(repo);
                continue;
            }

            int featuresFound = 0;
            int missingBindings = 0;
            for (var pf : project.getParsedFiles()) {
                featuresFound += pf.getApiCalls().size();
                missingBindings += pf.getMissingBindings().size();
            }

            addMinedRepo(repo, project, null);
            logger.info("Java version: " + project.getJavaSourceVersion() + " | Build successful: " + project.isBuildSuccess());
            logger.info("Features found: " + featuresFound);
            logger.info("Missing bindings: " + missingBindings);

            new LocCalculator(project);
            deleteRepo(repo);
        }
    }

    /**
     * Deletes the repository's cloned directory.
     *
     * @param repo The repository to delete.
     */
    private void deleteRepo(Repository repo) {
        try {
            logger.info("Deleting repository...");
            repo.deleteRepo();
            logger.info("Done");
        } catch (IOException e) {
            logger.error("Failed to delete the repository: ", e);
        }
    }

    /**
     * Determines whether a repository is an Android project by checking for an `AndroidManifest.xml` file.
     *
     * @param repoPath The path to the cloned repository.
     * @return True if the repository is an Android project, otherwise false.
     */
    private boolean isAndroidProject(Path repoPath) {
        var androidManifestFilter = FileFilterUtils.nameFileFilter("AndroidManifest.xml");
        Collection<File> files = FileUtils.listFiles(repoPath.toFile(), androidManifestFilter, TrueFileFilter.TRUE);

        return !files.isEmpty();
    }

    /**
     * Analyzes a repository to extract security features and metrics.
     *
     * @param repoPath The path to the cloned repository.
     * @return A {@link JavaProject} representing the analyzed repository.
     * @throws Exception If the analysis fails or times out.
     */
    private JavaProject analyzeRepo(Path repoPath) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<JavaProject> future = executor.submit(() -> {
            try {
                var featureLocator = new SecurityFeatureLocator(mappingPath);
                return featureLocator.locateFeatures(repoPath.toAbsolutePath().toString(), false);
            } catch (InterruptedException e) {
                return null;
            }
        });

        try {
            return future.get(5, TimeUnit.MINUTES);
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
        } finally {
            executor.shutdownNow();
        }

        return null;
    }
}
