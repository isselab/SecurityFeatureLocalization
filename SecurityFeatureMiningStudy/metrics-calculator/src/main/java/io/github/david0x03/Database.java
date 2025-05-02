package io.github.david0x03;

import io.github.david0x03.model.FeatureCounts;
import io.github.david0x03.model.File;
import io.github.david0x03.model.MinedRepository;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages database interactions for retrieving and processing data related to mined repositories, files, and features.
 */
public class Database {

    private static final Logger logger = LogManager.getLogger(Database.class);

    private static final Connection db;

    static {
        var url = System.getenv("DB_URL");
        var user = System.getenv("DB_USER");
        var password = System.getenv("DB_PASSWORD");

        try {
            db = DriverManager.getConnection(url, user, password);

            if (db == null)
                throw new RuntimeException("failed to connect to the database");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (!db.isClosed()) db.close();
                } catch (SQLException e) {
                    logger.error("Failed to gracefully close the database connection.", e);
                }
            }));
        } catch (SQLException e) {
            logger.error("Failed to establish a database connection.", e);
            System.exit(1);

            throw new IllegalStateException("Unreachable code");
        }
    }

    /**
     * Retrieves all mined repositories from the database.
     *
     * @return A list of {@link MinedRepository} objects.
     */
    public static List<MinedRepository> getAllMinedRepos() {
        var sql = "SELECT * FROM mined_repositories;";

        var minedRepos = new ArrayList<MinedRepository>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next()) {
                var id = rs.getLong("id");
                var repoId = rs.getLong("repository_id");
                var javaVersion = rs.getString("java_version");
                var buildSuccess = rs.getBoolean("build_success");
                var note = rs.getString("note");

                minedRepos.add(new MinedRepository(id, repoId, javaVersion, buildSuccess, note));
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed query repositories: ", e);
        }

        return minedRepos;
    }

    /**
     * Retrieves all files.
     *
     * @return A list of {@link File} objects.
     */
    public static List<File> getFiles() {
        var sql = "SELECT * FROM files;";

        var files = new ArrayList<File>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next()) {
                var id = rs.getLong("id");
                var minedRepoId = rs.getLong("mined_repository_id");
                var path = rs.getString("path");
                var lines = rs.getInt("lines");
                var linesOfCode = rs.getInt("lines_of_code");
                var commentLines = rs.getInt("comment_lines");
                var missingBindings = rs.getInt("missing_bindings");

                files.add(new File(id, minedRepoId, path, lines, linesOfCode, commentLines, missingBindings));
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed query files: ", e);
        }

        return files;
    }

    /**
     * Retrieves the total lines of code per repository.
     *
     * @return A map where the key is the repository ID and the value is the LOC count.
     */
    public static Map<Long, Integer> getLinesOfCodePerRepo() {
        var sql = """
                    SELECT r.id, SUM(fi.lines_of_code) as loc_count FROM mined_repositories r
                    INNER JOIN files fi ON r.id = fi.mined_repository_id
                    GROUP BY r.id;
                """;

        var locCounts = new HashMap<Long, Integer>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next()) {
                var fileId = rs.getLong("id");
                var locCount = rs.getInt("loc_count");

                locCounts.put(fileId, locCount);
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed query loc for repositories: ", e);
        }

        return locCounts;
    }

    /**
     * Retrieves the total number of comments per repository.
     *
     * @return A map where the key is the repository ID and the value is the comment line count.
     */
    public static Map<Long, Integer> getCommentsPerRepo() {
        var sql = """
                    SELECT r.id, SUM(fi.comment_lines) as comment_lines FROM mined_repositories r
                    INNER JOIN files fi ON r.id = fi.mined_repository_id
                    GROUP BY r.id;
                """;

        var commentLines = new HashMap<Long, Integer>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next()) {
                var fileId = rs.getLong("id");
                var comments = rs.getInt("comment_lines");

                commentLines.put(fileId, comments);
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed comments for repositories: ", e);
        }

        return commentLines;
    }

    /**
     * Retrieves feature counts grouped by repository.
     *
     * @return A map where the key is the repository ID and the value is a list of {@link FeatureCounts}.
     */
    public static Map<Long, List<FeatureCounts>> getFeatureCountsByRepo() {
        var sql = """
                    SELECT fi.mined_repository_id, fe.feature, COUNT(fe.id) AS feature_count
                    FROM files fi
                    INNER JOIN features fe ON fi.id = fe.file_id
                    GROUP BY fi.mined_repository_id, fe.feature
                """;

        var featureCounts = new HashMap<Long, List<FeatureCounts>>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next()) {
                var minedRepoId = rs.getLong("mined_repository_id");
                var feature = rs.getString("feature");
                var featureCount = rs.getInt("feature_count");

                var counts = featureCounts.getOrDefault(minedRepoId, new ArrayList<>());
                counts.add(new FeatureCounts(feature, featureCount));
                featureCounts.put(minedRepoId, counts);
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed query feature counts for repositories: ", e);
        }

        return featureCounts;
    }

    /**
     * Retrieves feature counts grouped by repository and file.
     *
     * @return A map where the key is the repository ID, and the value is a list of {@link FeatureCounts} objects
     * containing features and their respective counts.
     */
    public static Map<Long, List<FeatureCounts>> getFeatureCountsByRepoAndFile() {
        var sql = """
                    SELECT fi.mined_repository_id, fi.id, fe.feature, COUNT(fe.id) AS feature_count FROM files fi
                    INNER JOIN features fe ON fi.id = fe.file_id
                    GROUP BY fi.mined_repository_id, fi.id, fe.feature
                """;

        var featureCounts = new HashMap<Long, List<FeatureCounts>>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next()) {
                var minedRepoId = rs.getLong("mined_repository_id");
                var feature = rs.getString("feature");
                var featureCount = rs.getInt("feature_count");

                var counts = featureCounts.getOrDefault(minedRepoId, new ArrayList<>());
                counts.add(new FeatureCounts(feature, featureCount));
                featureCounts.put(minedRepoId, counts);
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed query feature counts for repositories and files: ", e);
        }

        return featureCounts;
    }

    /**
     * Retrieves the total number of files for each repository.
     *
     * @return A map where the key is the repository ID and the value is the count of files for that repository.
     */
    public static Map<Long, Integer> getFileCounts() {
        var sql = """
                    SELECT fi.mined_repository_id,
                    COUNT(fi.id) AS file_count FROM files fi
                    GROUP BY fi.mined_repository_id
                """;

        var fileCounts = new HashMap<Long, Integer>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next()) {
                var minedRepoId = rs.getLong("mined_repository_id");
                var fileCount = rs.getInt("file_count");

                fileCounts.put(minedRepoId, fileCount);
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed query file counts for repositories: ", e);
        }

        return fileCounts;
    }


    /**
     * Retrieves distinct feature names from the database.
     *
     * @return A list of distinct feature names.
     */
    public static List<String> getDistinctFeatures() {
        var sql = "SELECT DISTINCT feature from features;";

        var features = new ArrayList<String>();

        try {
            var stmt = db.createStatement();
            var rs = stmt.executeQuery(sql);

            while (rs.next())
                features.add(rs.getString("feature"));

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            logger.error("Failed query distinct features: ", e);
        }

        return features;
    }

}
