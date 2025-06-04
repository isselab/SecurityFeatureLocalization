package io.github.david0x03.metrics;

import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.david0x03.Database;
import io.github.david0x03.FeatureUtils;

/**
 * Calculates basic metrics and feature distributions for mined repositories.
 * Includes statistics for successfully mined repositories, rejected
 * repositories, and
 * feature distributions across repositories.
 */
public class BasicMetrics {

	private final Logger logger = LogManager.getLogger(BasicMetrics.class);

	private final List<String> mainFeatures;

	/**
	 * Initializes the BasicMetrics object and logs statistics for mined
	 * repositories.
	 * It categorizes repositories into successfully mined, Android projects, and
	 * failed cases.
	 */
	public BasicMetrics() {
		final var minedRepos = Database.getAllMinedRepos();
		final var distinctFeatures = Database.getDistinctFeatures();

		// Extract main categories of features
		this.mainFeatures = distinctFeatures.stream().map(FeatureUtils::getSecurityFeatureMainCategory).distinct()
				.toList();

		var androidProjects = 0;
		var successfullyMined = 0;

		for (final var minedRepo : minedRepos) {
			if (minedRepo.note() == null) {
				successfullyMined++;
			} else if (minedRepo.note().equals("android project")) {
				androidProjects++;
			}
		}

		final var failedToMine = minedRepos.size() - successfullyMined - androidProjects;

		this.logger.info("Total mined repositories: " + minedRepos.size());
		this.logger.info("Rejected: " + (minedRepos.size() - successfullyMined) + " (android: " + androidProjects
				+ " | failed: " + failedToMine + ")");
		this.logger.info("Successfully mined: " + successfullyMined);
	}

	/**
	 * Calculates the distribution of security features across repositories.
	 * Excludes outliers based on the 5th and 95th percentiles.
	 *
	 * @return A map where keys are feature names and values are their average
	 *         distributions as percentages.
	 */
	public HashMap<String, Double> calcDistribution() {
		final var featureDistribution = new HashMap<String, DescriptiveStatistics>();
		this.mainFeatures.forEach(f -> featureDistribution.put(f, new DescriptiveStatistics()));

		final var featureCounts = Database.getFeatureCountsByRepoAndFile();
		final var fileCounts = Database.getFileCounts();

		// Calculate feature occurrences across repositories
		featureCounts.forEach((minedRepoId, counts) -> {
			for (final var feature : this.mainFeatures) {
				final var occurrences = counts.stream().filter(fCount -> {
					final var f = FeatureUtils.getSecurityFeatureMainCategory(fCount.feature());
					return f.equals(feature);
				}).toList();

				if (occurrences.isEmpty()) {
					continue;
				}
				final var zeroRatio = (double) occurrences.size() / (double) fileCounts.get(minedRepoId);

				featureDistribution.get(feature).addValue(zeroRatio);
			}
		});

		// Compute average distributions excluding outliers
		final var averages = new HashMap<String, Double>();
		featureDistribution.forEach((feature, stats) -> {

			final var lowerPercentile = stats.getPercentile(5);
			final var upperPercentile = stats.getPercentile(95);

			var sum = 0D;
			var count = 0;
			for (final double value : stats.getValues()) {
				if (value >= lowerPercentile && value <= upperPercentile) {
					sum += value;
					count++;
				}
			}

			final var meanWithoutOutliers = sum / count * 100;
			averages.put(feature, meanWithoutOutliers);
		});

		return averages;
	}
}
