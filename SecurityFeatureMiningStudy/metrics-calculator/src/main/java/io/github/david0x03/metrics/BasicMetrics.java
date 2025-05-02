package io.github.david0x03.metrics;

import io.github.david0x03.Database;
import io.github.david0x03.FeatureUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.List;

/**
 * Calculates basic metrics and feature distributions for mined repositories.
 * Includes statistics for successfully mined repositories, rejected repositories, and
 * feature distributions across repositories.
 */
public class BasicMetrics {

    private final Logger logger = LogManager.getLogger(BasicMetrics.class);

    private final List<String> mainFeatures;

    /**
     * Initializes the BasicMetrics object and logs statistics for mined repositories.
     * It categorizes repositories into successfully mined, Android projects, and failed cases.
     */
    public BasicMetrics() {
        var minedRepos = Database.getAllMinedRepos();
        var distinctFeatures = Database.getDistinctFeatures();

        // Extract main categories of features
        mainFeatures = distinctFeatures.stream().map(FeatureUtils::getSecurityFeatureMainCategory).distinct().toList();

        int androidProjects = 0;
        int successfullyMined = 0;

        for (var minedRepo : minedRepos) {
            if (minedRepo.note() == null)
                successfullyMined++;
            else if (minedRepo.note().equals("android project"))
                androidProjects++;
        }

        int failedToMine = minedRepos.size() - successfullyMined - androidProjects;

        logger.info("Total mined repositories: " + minedRepos.size());
        logger.info("Rejected: " + (minedRepos.size() - successfullyMined) + " (android: " + androidProjects + " | failed: " + failedToMine + ")");
        logger.info("Successfully mined: " + successfullyMined);
    }

    /**
     * Calculates the distribution of security features across repositories.
     * Excludes outliers based on the 5th and 95th percentiles.
     *
     * @return A map where keys are feature names and values are their average distributions as percentages.
     */
    public HashMap<String, Double> calcDistribution() {
        var featureDistribution = new HashMap<String, DescriptiveStatistics>();
        mainFeatures.forEach(f -> featureDistribution.put(f, new DescriptiveStatistics()));

        var featureCounts = Database.getFeatureCountsByRepoAndFile();
        var fileCounts = Database.getFileCounts();

        // Calculate feature occurrences across repositories
        featureCounts.forEach((minedRepoId, counts) -> {
            for (var feature : mainFeatures) {
                var occurrences = counts.stream().filter(fCount -> {
                    var f = FeatureUtils.getSecurityFeatureMainCategory(fCount.feature());
                    return f.equals(feature);
                }).toList();

                if (occurrences.isEmpty()) continue;
                var zeroRatio = (double) occurrences.size() / (double) fileCounts.get(minedRepoId);

                featureDistribution.get(feature).addValue(zeroRatio);
            }
        });

        // Compute average distributions excluding outliers
        var averages = new HashMap<String, Double>();
        featureDistribution.forEach((feature, stats) -> {

            double lowerPercentile = stats.getPercentile(5);
            double upperPercentile = stats.getPercentile(95);

            double sum = 0;
            int count = 0;
            for (double value : stats.getValues()) {
                if (value >= lowerPercentile && value <= upperPercentile) {
                    sum += value;
                    count++;
                }
            }

            double meanWithoutOutliers = sum / count * 100;
            averages.put(feature, meanWithoutOutliers);
        });

        return averages;
    }
}
