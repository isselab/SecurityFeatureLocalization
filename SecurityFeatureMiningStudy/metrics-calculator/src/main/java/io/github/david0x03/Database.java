package io.github.david0x03;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.david0x03.model.FeatureCounts;
import io.github.david0x03.model.File;
import io.github.david0x03.model.MinedRepository;

/**
 * Manages database interactions for retrieving and processing data related to
 * mined repositories, files, and features.
 */
public class Database {

	private static final Logger logger = LogManager.getLogger(Database.class);

	private static final Connection db;

	static {
		final var url = System.getenv("DB_URL");
		final var user = System.getenv("DB_USER");
		final var password = System.getenv("DB_PASSWORD");

		try {
			db = DriverManager.getConnection(url, user, password);

			if (db == null) {
				throw new RuntimeException("failed to connect to the database");
			}

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					if (!db.isClosed()) {
						db.close();
					}
				} catch (final SQLException e) {
					logger.error("Failed to gracefully close the database connection.", e);
				}
			}));
		} catch (final SQLException e) {
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
		final var sql = "SELECT * FROM mined_repositories;";

		final var minedRepos = new ArrayList<MinedRepository>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				final var id = rs.getLong("id");
				final var repoId = rs.getLong("repository_id");
				final var javaVersion = rs.getString("java_version");
				final var buildSuccess = rs.getBoolean("build_success");
				final var note = rs.getString("note");

				minedRepos.add(new MinedRepository(id, repoId, javaVersion, buildSuccess, note));
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
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
		final var sql = "SELECT * FROM files;";

		final var files = new ArrayList<File>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				final var id = rs.getLong("id");
				final var minedRepoId = rs.getLong("mined_repository_id");
				final var path = rs.getString("path");
				final var lines = rs.getInt("lines");
				final var linesOfCode = rs.getInt("lines_of_code");
				final var commentLines = rs.getInt("comment_lines");
				final var missingBindings = rs.getInt("missing_bindings");

				files.add(new File(id, minedRepoId, path, lines, linesOfCode, commentLines, missingBindings));
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
			logger.error("Failed query files: ", e);
		}

		return files;
	}

	/**
	 * Retrieves the total lines of code per repository.
	 *
	 * @return A map where the key is the repository ID and the value is the LOC
	 *         count.
	 */
	public static Map<Long, Integer> getLinesOfCodePerRepo() {
		final var sql = """
				    SELECT r.id, SUM(fi.lines_of_code) as loc_count FROM mined_repositories r
				    INNER JOIN files fi ON r.id = fi.mined_repository_id
				    GROUP BY r.id;
				""";

		final var locCounts = new HashMap<Long, Integer>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				final var fileId = rs.getLong("id");
				final var locCount = rs.getInt("loc_count");

				locCounts.put(fileId, locCount);
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
			logger.error("Failed query loc for repositories: ", e);
		}

		return locCounts;
	}

	/**
	 * Retrieves the total number of comments per repository.
	 *
	 * @return A map where the key is the repository ID and the value is the comment
	 *         line count.
	 */
	public static Map<Long, Integer> getCommentsPerRepo() {
		final var sql = """
				    SELECT r.id, SUM(fi.comment_lines) as comment_lines FROM mined_repositories r
				    INNER JOIN files fi ON r.id = fi.mined_repository_id
				    GROUP BY r.id;
				""";

		final var commentLines = new HashMap<Long, Integer>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				final var fileId = rs.getLong("id");
				final var comments = rs.getInt("comment_lines");

				commentLines.put(fileId, comments);
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
			logger.error("Failed comments for repositories: ", e);
		}

		return commentLines;
	}

	/**
	 * Retrieves feature counts grouped by repository.
	 *
	 * @return A map where the key is the repository ID and the value is a list of
	 *         {@link FeatureCounts}.
	 */
	public static Map<Long, List<FeatureCounts>> getFeatureCountsByRepo() {
		final var sql = """
				    SELECT fi.mined_repository_id, fe.feature, COUNT(fe.id) AS feature_count
				    FROM files fi
				    INNER JOIN features fe ON fi.id = fe.file_id
				    GROUP BY fi.mined_repository_id, fe.feature
				""";

		final var featureCounts = new HashMap<Long, List<FeatureCounts>>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				final var minedRepoId = rs.getLong("mined_repository_id");
				final var feature = rs.getString("feature");
				final var featureCount = rs.getInt("feature_count");

				final var counts = featureCounts.getOrDefault(minedRepoId, new ArrayList<>());
				counts.add(new FeatureCounts(feature, featureCount));
				featureCounts.put(minedRepoId, counts);
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
			logger.error("Failed query feature counts for repositories: ", e);
		}

		return featureCounts;
	}

	/**
	 * Retrieves feature counts grouped by repository and file.
	 *
	 * @return A map where the key is the repository ID, and the value is a list of
	 *         {@link FeatureCounts} objects
	 *         containing features and their respective counts.
	 */
	public static Map<Long, List<FeatureCounts>> getFeatureCountsByRepoAndFile() {
		final var sql = """
				    SELECT fi.mined_repository_id, fi.id, fe.feature, COUNT(fe.id) AS feature_count FROM files fi
				    INNER JOIN features fe ON fi.id = fe.file_id
				    GROUP BY fi.mined_repository_id, fi.id, fe.feature
				""";

		final var featureCounts = new HashMap<Long, List<FeatureCounts>>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				final var minedRepoId = rs.getLong("mined_repository_id");
				final var feature = rs.getString("feature");
				final var featureCount = rs.getInt("feature_count");

				final var counts = featureCounts.getOrDefault(minedRepoId, new ArrayList<>());
				counts.add(new FeatureCounts(feature, featureCount));
				featureCounts.put(minedRepoId, counts);
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
			logger.error("Failed query feature counts for repositories and files: ", e);
		}

		return featureCounts;
	}

	/**
	 * Retrieves the total number of files for each repository.
	 *
	 * @return A map where the key is the repository ID and the value is the count
	 *         of files for that repository.
	 */
	public static Map<Long, Integer> getFileCounts() {
		final var sql = """
				    SELECT fi.mined_repository_id,
				    COUNT(fi.id) AS file_count FROM files fi
				    GROUP BY fi.mined_repository_id
				""";

		final var fileCounts = new HashMap<Long, Integer>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				final var minedRepoId = rs.getLong("mined_repository_id");
				final var fileCount = rs.getInt("file_count");

				fileCounts.put(minedRepoId, fileCount);
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
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
		final var sql = "SELECT DISTINCT feature from features;";

		final var features = new ArrayList<String>();

		try {
			final var stmt = db.createStatement();
			final var rs = stmt.executeQuery(sql);

			while (rs.next()) {
				features.add(rs.getString("feature"));
			}

			rs.close();
			stmt.close();
		} catch (final SQLException e) {
			logger.error("Failed query distinct features: ", e);
		}

		return features;
	}

}
