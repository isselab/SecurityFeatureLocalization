package io.github.david0x03;

import io.github.david0x03.metrics.FileMetrics;
import io.github.david0x03.metrics.LocCalculator;
import io.github.david0x03.project.JavaProject;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Manages database interactions for storing mined repositories, files, and features.
 * Also tracks failed repository mining attempts.
 */
public class Database {

    private static final Logger logger = LogManager.getLogger(Database.class);

    private static final String CREATE_MINED_REPOSITORIES_TABLE = """
            CREATE TABLE IF NOT EXISTS mined_repositories (
                id BIGSERIAL PRIMARY KEY,
                repository_id BIGINT UNIQUE,
                java_version VARCHAR(255),
                build_success BOOLEAN,
                note VARCHAR(255),
                FOREIGN KEY (repository_id) REFERENCES repositories(id)
            );""";

    private static final String UPSERT_MINED_REPOSITORY = """
            INSERT INTO mined_repositories (repository_id, java_version, build_success, note)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (repository_id)
            DO UPDATE SET
                java_version = EXCLUDED.java_version,
                build_success = EXCLUDED.build_success,
                note = EXCLUDED.note
            RETURNING id;
            """;

    private static final String CREATE_FILES_TABLE = """
            CREATE TABLE IF NOT EXISTS files (
                id BIGSERIAL PRIMARY KEY,
                mined_repository_id BIGINT,
                path VARCHAR(255),
                lines INTEGER,
                lines_of_code INTEGER,
                comment_lines INTEGER,
                missing_bindings INTEGER,
                FOREIGN KEY (mined_repository_id) REFERENCES mined_repositories(id)
            );""";

    private static final String INSERT_FILE = """
            INSERT INTO files (mined_repository_id, path, lines, lines_of_code, comment_lines, missing_bindings)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id;
            """;

    private static final String CREATE_FEATURES_TABLE = """
            CREATE TABLE IF NOT EXISTS features (
                id BIGSERIAL PRIMARY KEY,
                file_id BIGINT,
                line INTEGER,
                api VARCHAR(255),
                feature VARCHAR(255),
                FOREIGN KEY (file_id) REFERENCES files(id)
            );""";

    private static final String INSERT_FEATURE = """
            INSERT INTO features (file_id, line, api, feature)
            VALUES (?, ?, ?, ?)
            RETURNING id;
            """;

    private static final Connection db;

    static {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        try {
            db = DriverManager.getConnection(url, user, password);

            db.createStatement().executeUpdate(CREATE_MINED_REPOSITORIES_TABLE);
            db.createStatement().executeUpdate(CREATE_FILES_TABLE);
            db.createStatement().executeUpdate(CREATE_FEATURES_TABLE);

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

    private Database() {
    }

    /**
     * Retrieves the next repository from the database that has not been mined yet.
     *
     * @return A {@link Repository} object or null if no un-mined repository was found.
     */
    public static Repository getRepository() {
        String sql = """
                SELECT * FROM repositories
                WHERE NOT EXISTS (
                    SELECT repository_id FROM mined_repositories
                    WHERE mined_repositories.repository_id = repositories.id
                )
                LIMIT 1;
                """;

        try {
            var pStmt = db.prepareStatement(sql);
            var res = pStmt.executeQuery();

            if (!res.next()) return null;

            long id = res.getLong("id");
            String url = res.getString("url");
            String owner = res.getString("owner");
            String name = res.getString("name");
            String createdAt = res.getString("created_at");
            int stars = res.getInt("stars");
            int size = res.getInt("size");
            return new Repository(id, url, owner, name, createdAt, stars, size);
        } catch (SQLException e) {
            logger.error("Failed to query repository: ", e);
            return null;
        }
    }

    /**
     * Adds a mined repository, its files, and features to the database.
     *
     * @param repo    The {@link Repository} object representing the mined repository.
     * @param project The {@link JavaProject} containing the mined data.
     * @param note    An optional note to denote an error.
     */
    public static void addMinedRepo(Repository repo, JavaProject project, String note) {
        long minedRepoId;
        try {
            var pStmt = db.prepareStatement(UPSERT_MINED_REPOSITORY);
            pStmt.setLong(1, repo.getId());
            pStmt.setString(2, project.getJavaSourceVersion());
            pStmt.setBoolean(3, project.isBuildSuccess());
            pStmt.setString(4, note);
            var res = pStmt.executeQuery();

            res.next();
            minedRepoId = res.getLong("id");
        } catch (SQLException e) {
            logger.error("Failed to insert mined repository: " + repo.getId(), e);
            return;
        }

        var fileMetrics = new LocCalculator(project).getFileMetrics();
        fileMetrics.forEach(fm -> addRepoFile(minedRepoId, project, fm));
    }

    /**
     * Adds a file and its associated features to the database.
     *
     * @param minedRepoId The ID of the mined repository to associate the file with.
     * @param project     The {@link JavaProject} containing the file.
     * @param fileMetric  The {@link FileMetrics} containing file metrics and features.
     */
    private static void addRepoFile(long minedRepoId, JavaProject project, FileMetrics fileMetric) {
        long fileId;
        try {
            var relFilePath = project.getProjectPath().relativize(fileMetric.getParsedFile().getFilePath());

            PreparedStatement pStmt = db.prepareStatement(INSERT_FILE);
            pStmt.setLong(1, minedRepoId);
            pStmt.setString(2, relFilePath.toString());
            pStmt.setInt(3, fileMetric.getLines());
            pStmt.setInt(4, fileMetric.getLinesOfCode());
            pStmt.setInt(5, fileMetric.getCommentedLines());
            pStmt.setInt(6, fileMetric.getParsedFile().getMissingBindings().size());
            var res = pStmt.executeQuery();

            res.next();
            fileId = res.getLong("id");
        } catch (SQLException e) {
            logger.error("Failed to insert file: ", e);
            return;
        }

        try {
            PreparedStatement pStmt = db.prepareStatement(INSERT_FEATURE);
            for (var apiCall : fileMetric.getParsedFile().getApiCalls()) {
                for (var feature : apiCall.getFeatures()) {
                    pStmt.setLong(1, fileId);
                    pStmt.setInt(2, apiCall.start.line());
                    pStmt.setString(3, apiCall.qualifiedName);
                    pStmt.setString(4, feature);
                    pStmt.executeQuery();
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to insert feature: ", e);
        }
    }

    /**
     * Records a failed repository mining attempt in the database.
     *
     * @param repo The {@link Repository} that failed to mine.
     * @param note A note explaining the reason for the failure.
     */
    public static void addFailedRepoMining(Repository repo, String note) {
        try {
            var pStmt = db.prepareStatement(UPSERT_MINED_REPOSITORY);
            pStmt.setLong(1, repo.getId());
            pStmt.setString(2, null);
            pStmt.setBoolean(3, false);
            pStmt.setString(4, note);
            pStmt.execute();
        } catch (SQLException e) {
            logger.error("Failed to insert mined repository: " + repo.getId(), e);
        }
    }
}
