package io.github.david0x03;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHRepository;

/**
 * Manages the database connection and operations for storing repository data.
 * This class is responsible for initializing the database schema and providing
 * methods
 * to insert or update repository records.
 */
public class Database {

	private static final Logger logger = LogManager.getLogger(Database.class);

	private static final Connection db;

	// Static block for initializing the database connection and schema
	static {
		final var url = System.getenv("DB_URL");
		final var user = System.getenv("DB_USER");
		final var password = System.getenv("DB_PASSWORD");

		try {
			db = DriverManager.getConnection(url, user, password);

			final var stmt = db.createStatement();
			stmt.executeUpdate("""
					CREATE TABLE IF NOT EXISTS repositories (
					    id BIGINT PRIMARY KEY,
					    url TEXT,
					    owner VARCHAR(255),
					    name VARCHAR(255),
					    created_at VARCHAR(255),
					    stars INTEGER,
					    size INTEGER
					);""");

			// always try to gracefully shut down the database
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

	private Database() {
	}

	/**
	 * Adds or updates a repository record in the database.
	 * If the repository ID already exists, the record is updated with the new data.
	 *
	 * @param repo The {@link GHRepository} object containing the repository data.
	 */
	public static void addRepo(final GHRepository repo) {
		final var sql = """
				INSERT INTO repositories (id, url, owner, name, created_at, stars, size)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id)
				DO UPDATE SET
				    url = EXCLUDED.url,
				    owner = EXCLUDED.owner,
				    name = EXCLUDED.name,
				    created_at = EXCLUDED.created_at,
				    stars = EXCLUDED.stars,
				    size = EXCLUDED.size;
				""";

		try {
			final var pStmt = db.prepareStatement(sql);

			pStmt.setLong(1, repo.getId());
			pStmt.setString(2, repo.getHtmlUrl().toString());
			pStmt.setString(3, repo.getOwnerName());
			pStmt.setString(4, repo.getName());
			pStmt.setString(5, repo.getCreatedAt().toString());
			pStmt.setInt(6, repo.getStargazersCount());
			pStmt.setInt(7, repo.getSize());

			pStmt.execute();
		} catch (SQLException | IOException e) {
			logger.error("Failed to insert repository: " + repo.getId(), e);
		}
	}
}
