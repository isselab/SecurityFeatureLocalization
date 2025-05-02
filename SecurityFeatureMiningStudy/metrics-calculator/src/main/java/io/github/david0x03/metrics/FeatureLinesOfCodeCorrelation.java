package io.github.david0x03.metrics;

import io.github.david0x03.Database;
import io.github.david0x03.FeatureUtils;
import io.github.david0x03.model.FeatureCounts;
import io.github.david0x03.model.Pair;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Calculates the correlation between security features and logical lines of code
 * across repositories. Supports global and per-feature analysis.
 */
public class FeatureLinesOfCodeCorrelation {
    private final List<String> mainFeatures;

    public FeatureLinesOfCodeCorrelation() {
        var distinctFeatures = Database.getDistinctFeatures();
        mainFeatures = distinctFeatures.stream().map(FeatureUtils::getSecurityFeatureMainCategory).distinct().toList();
    }

    /**
     * Calculates the global correlation between the total number of security features
     * and the total lines of code across repositories using Spearman's rank correlation.
     *
     * @return A {@link Pair} containing:
     * - The correlation coefficient.
     * - The p-value for statistical significance (two-tailed test).
     */
    public Pair<Double> calcCorrelationGlobally() {
        var featureCounts = Database.getFeatureCountsByRepo();
        var locCounts = Database.getLinesOfCodePerRepo();

        var featureData = new ArrayList<Integer>();
        var locData = new ArrayList<Integer>();

        featureCounts.forEach((repoId, counts) -> {
            var totalFeatures = counts.stream().mapToInt(FeatureCounts::count).sum();
            var loc = locCounts.getOrDefault(repoId, 0);

            if (totalFeatures == 0 || loc == 0) return;

            featureData.add(totalFeatures);
            locData.add(loc);
        });

        var dataMatrix = new BlockRealMatrix(featureData.size(), 2);
        for (var i = 0; i < featureData.size(); i++) {
            dataMatrix.setEntry(i, 0, featureData.get(i));
            dataMatrix.setEntry(i, 1, locData.get(i));
        }

        var a = new SpearmansCorrelation(dataMatrix);
        var corr = a.getCorrelationMatrix().getEntry(0, 1);

        // Calculate p-value using T-distribution
        double tStatistic = corr * Math.sqrt((dataMatrix.getRowDimension() - 2) / (1 - Math.pow(corr, 2)));
        TDistribution tDist = new TDistribution(dataMatrix.getRowDimension() - 2);

        // Two-tailed test
        var p = 2 * (1 - tDist.cumulativeProbability(Math.abs(tStatistic)));

        return new Pair<>(corr, p);
    }

    /**
     * Calculates the correlation between lines of code and specific security features
     * across repositories using Spearman's rank correlation.
     *
     * @return A map where keys are feature names and values are {@link Pair} objects containing:
     * - The correlation coefficient for the feature.
     * - The p-value for statistical significance (two-tailed test).
     */
    public HashMap<String, Pair<Double>> calcCorrelationGloballyPerFeature() {
        var correlations = new HashMap<String, Pair<Double>>();

        var featureCounts = Database.getFeatureCountsByRepo();
        var locCounts = Database.getLinesOfCodePerRepo();

        for (var f : mainFeatures) {
            var featureData = new ArrayList<Integer>();
            var locData = new ArrayList<Integer>();

            featureCounts.forEach((repoId, counts) -> {
                var totalFeatures = counts.stream().filter(fCount -> f.equals(FeatureUtils.getSecurityFeatureMainCategory(fCount.feature()))).mapToInt(FeatureCounts::count).sum();
                var loc = locCounts.getOrDefault(repoId, 0);

                if (totalFeatures == 0 || loc == 0) return;

                featureData.add(totalFeatures);
                locData.add(loc);
            });

            var dataMatrix = new BlockRealMatrix(featureData.size(), 2);
            for (var i = 0; i < featureData.size(); i++) {
                dataMatrix.setEntry(i, 0, featureData.get(i));
                dataMatrix.setEntry(i, 1, locData.get(i));
            }

            var a = new SpearmansCorrelation(dataMatrix);
            var corr = a.getCorrelationMatrix().getEntry(0, 1);

            // Calculate p-value using T-distribution
            double tStatistic = corr * Math.sqrt((dataMatrix.getRowDimension() - 2) / (1 - Math.pow(corr, 2)));
            TDistribution tDist = new TDistribution(dataMatrix.getRowDimension() - 2);

            // Two-tailed test
            var p = 2 * (1 - tDist.cumulativeProbability(Math.abs(tStatistic)));

            correlations.put(f, new Pair<>(corr, p));
        }

        return correlations;
    }
}
