package io.github.david0x03;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Handles the mining of GitHub repositories by querying repositories based on
 * specific criteria and storing the results in a database.
 */
public class GitHubMiner {

	private static final Logger logger = LogManager.getLogger(GitHubMiner.class);

	private static final int MIN_SIZE = 10 * 1000; // in bytes
	private static final int MIN_STARS = 10;

	private final GitHub github;

	/**
	 * Initializes a GitHubMiner instance with a connection to the GitHub API.
	 */
	public GitHubMiner() {
		try {
			this.github = GitHubBuilder.fromEnvironment().build();
		} catch (final IOException e) {
			logger.error("Failed to initialize GitHub connection from environment variables.", e);
			System.exit(1);

			throw new IllegalStateException("Unreachable code");
		}
	}

	/**
	 * Mines GitHub repositories for each month since January 2010 and adds the
	 * accepted
	 * repositories to the database. Repositories are filtered by size, stars, and
	 * presence
	 * of a Maven or Gradle build file.
	 *
	 * @param max         The maximum number of repositories to mine. Use -1 for no
	 *                    limit.
	 * @param maxPerMonth The maximum number of repositories to mine per month.
	 *                    Cannot exceed 1000.
	 */
	public void mineRepositories(final int max, final int maxPerMonth) {
		var totalRepos = 0;
		var acceptedRepos = 0;

		final var today = Calendar.getInstance();
		var year = 2010;
		var month = 1;

		while (max == -1 || totalRepos < max) {
			final var res = this.queryRepos(year, month, maxPerMonth);

			totalRepos += res.totalRepos();
			acceptedRepos += res.acceptedRepos().size();

			if (year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH) + 1) {
				break;
			}
			if (month == 12) {
				month = 1;
				year++;
			} else {
				month++;
			}
		}

		logger.info("Total queried " + totalRepos + " repositories");
		logger.info("Total accepted " + acceptedRepos + " | Total rejected: " + (totalRepos - acceptedRepos));
	}

	/**
	 * Queries GitHub for repositories created in a specific month and year.
	 * Filters repositories by size, stars, and presence of a Maven or Gradle build
	 * file.
	 *
	 * @param year  The year to query.
	 * @param month The month to query.
	 * @param max   The maximum number of repositories to query for this period.
	 * @return A {@link QueryResult} containing accepted repositories and the total
	 *         queried count.
	 */
	private QueryResult queryRepos(final int year, final int month, final int max) {
		final var created = year + "-" + String.format("%02d", month); // e.g. 2015-10

		logger.info("Querying for: " + created);

		final var query = this.github.searchRepositories()
				.created(created)
				.language("Java")
				.size(">=" + MIN_SIZE)
				.stars(">=" + MIN_STARS)
				.q("NOT android");

		final var repos = query.list().withPageSize(100);
		final var acceptedRepos = new ArrayList<GHRepository>();

		var totalRepos = 0;
		for (final var repo : repos) {
			if (totalRepos >= max) {
				break;
			}
			totalRepos++;

			final var accepted = this.repoHasFile(repo, "pom.xml") || this.repoHasFile(repo, "build.gradle");
			if (accepted) {
				acceptedRepos.add(repo);
				Database.addRepo(repo);
			}

			logger.info(repo.getHtmlUrl() + " | Accepted: " + accepted);
		}

		logger.info("Queried " + totalRepos + " repositories");
		logger.info("Accepted " + acceptedRepos.size() + " | Rejected: " + (totalRepos - acceptedRepos.size()));

		return new QueryResult(acceptedRepos, totalRepos);
	}

	/**
	 * Checks if a repository contains a specific file.
	 *
	 * @param repo The repository to check.
	 * @param file The name of the file to look for (e.g., "pom.xml").
	 * @return True if the file exists in the repository, otherwise false.
	 */
	private boolean repoHasFile(final GHRepository repo, final String file) {
		try {
			final var content = repo.getFileContent(file);
			return content.isFile();
		} catch (final IOException e) {
			return false;
		}
	}

	/**
	 * Represents the result of a GitHub repository query.
	 *
	 * @param acceptedRepos The list of repositories that met the acceptance
	 *                      criteria.
	 * @param totalRepos    The total number of repositories queried.
	 */
	private record QueryResult(List<GHRepository> acceptedRepos, int totalRepos) {
	}
}
