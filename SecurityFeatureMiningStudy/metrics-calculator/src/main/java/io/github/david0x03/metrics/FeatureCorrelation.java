package io.github.david0x03.metrics;

import io.github.david0x03.Database;
import io.github.david0x03.FeatureUtils;
import io.github.david0x03.model.Pair;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Calculates correlations between security features based on their presence or counts
 * across repositories.
 */
public class FeatureCorrelation {
    private final List<String> mainFeatures;

    public FeatureCorrelation() {
        var distinctFeatures = Database.getDistinctFeatures();
        mainFeatures = distinctFeatures.stream().map(FeatureUtils::getSecurityFeatureMainCategory).distinct().toList();
    }

    /**
     * Calculates the pairwise correlation of security features based on their presence in repositories.
     *
     * @return A nested map where keys are feature pairs, and values are {@link Pair} objects containing:
     * - The correlation coefficient.
     * - The p-value for statistical significance.
     */
    public HashMap<String, HashMap<String, Pair<Double>>> calcCorrelationGloballyPresenceBased() {
        var correlations = new HashMap<String, HashMap<String, Pair<Double>>>();
        mainFeatures.forEach(f -> correlations.put(f, new HashMap<>()));

        var featureCounts = Database.getFeatureCountsByRepo();

        for (var f1 : mainFeatures) {
            for (var f2 : mainFeatures) {
                var f1DataPoints = new ArrayList<Integer>();
                var f2DataPoints = new ArrayList<Integer>();

                featureCounts.forEach((fileId, counts) -> {
                    var f1Count = counts.stream().filter(fCount -> FeatureUtils.getSecurityFeatureMainCategory(fCount.feature()).equals(f1)).findFirst();
                    var f2Count = counts.stream().filter(fCount -> FeatureUtils.getSecurityFeatureMainCategory(fCount.feature()).equals(f2)).findFirst();

                    if (f1Count.isPresent()) f1DataPoints.add(1);
                    else f1DataPoints.add(0);

                    if (f2Count.isPresent()) f2DataPoints.add(1);
                    else f2DataPoints.add(0);
                });

                var dataMatrix = new BlockRealMatrix(f1DataPoints.size(), 2);
                for (var i = 0; i < f1DataPoints.size(); i++) {
                    dataMatrix.setEntry(i, 0, f1DataPoints.get(i));
                    dataMatrix.setEntry(i, 1, f2DataPoints.get(i));
                }

                if (f1DataPoints.size() < 2) continue;

                var a = new PearsonsCorrelation(dataMatrix);
                var corr = a.getCorrelationMatrix().getEntry(0, 1);
                var p = a.getCorrelationPValues().getEntry(0, 1);

                correlations.get(f1).put(f2, new Pair<>(corr, p));
            }
        }

        return correlations;
    }


    /**
     * Calculates the pairwise correlation of security features based on their counts in repositories.
     *
     * @return A nested map where keys are feature pairs, and values are {@link Pair} objects containing:
     * - The correlation coefficient.
     * - The p-value for statistical significance.
     */
    public HashMap<String, HashMap<String, Pair<Double>>> calcCorrelationGloballyCountBased() {
        var correlations = new HashMap<String, HashMap<String, Pair<Double>>>();
        mainFeatures.forEach(f -> correlations.put(f, new HashMap<>()));

        var featureCounts = Database.getFeatureCountsByRepo();

        for (var f1 : mainFeatures) {
            for (var f2 : mainFeatures) {
                var f1DataPoints = new ArrayList<Integer>();
                var f2DataPoints = new ArrayList<Integer>();

                featureCounts.forEach((fileId, counts) -> {
                    var f1Count = counts.stream().filter(fCount -> FeatureUtils.getSecurityFeatureMainCategory(fCount.feature()).equals(f1)).findFirst();
                    var f2Count = counts.stream().filter(fCount -> FeatureUtils.getSecurityFeatureMainCategory(fCount.feature()).equals(f2)).findFirst();

                    // if (f1Count.isEmpty() && f2Count.isEmpty()) return;

                    if (f1Count.isPresent()) f1DataPoints.add(f1Count.get().count());
                    else f1DataPoints.add(0);

                    if (f2Count.isPresent()) f2DataPoints.add(f2Count.get().count());
                    else f2DataPoints.add(0);
                });

                var dataMatrix = new BlockRealMatrix(f1DataPoints.size(), 2);
                for (var i = 0; i < f1DataPoints.size(); i++) {
                    dataMatrix.setEntry(i, 0, f1DataPoints.get(i));
                    dataMatrix.setEntry(i, 1, f2DataPoints.get(i));
                }

                if (f1DataPoints.size() < 2) continue;

                var b = new SpearmansCorrelation(dataMatrix);
                var corr = b.getCorrelationMatrix().getEntry(0, 1);

                // Calculate p-value using T-distribution
                double tStatistic = corr * Math.sqrt((dataMatrix.getRowDimension() - 2) / (1 - Math.pow(corr, 2)));
                TDistribution tDist = new TDistribution(dataMatrix.getRowDimension() - 2);

                // Two-tailed test
                var p = 2 * (1 - tDist.cumulativeProbability(Math.abs(tStatistic)));

                correlations.get(f1).put(f2, new Pair<>(corr, p));
            }
        }

        return correlations;
    }
}
