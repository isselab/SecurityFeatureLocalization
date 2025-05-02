package io.github.david0x03;

import io.github.david0x03.metrics.BasicMetrics;
import io.github.david0x03.metrics.FeatureCommentCorrelation;
import io.github.david0x03.metrics.FeatureCorrelation;
import io.github.david0x03.metrics.FeatureLinesOfCodeCorrelation;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    @FunctionalInterface
    public interface CSVFunction {
        void apply(CSVPrinter csvPrinter) throws IOException;
    }

    public static void main(String[] args) {
        var basicMetrics = new BasicMetrics();
        var featureCorrelation = new FeatureCorrelation();
        var featureLinesOfCodeCorrelation = new FeatureLinesOfCodeCorrelation();
        var featureCommentCorrelation = new FeatureCommentCorrelation();

        var metricDir = Paths.get("metrics");
        if (!Files.isDirectory(metricDir)) {
            try {
                Files.createDirectory(metricDir);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        var featureDistribution = basicMetrics.calcDistribution();
        printCSV(new String[]{"feature", "distribution"}, "metrics/featureDistribution.csv", (csvPrinter -> {
            for (var entry : featureDistribution.entrySet())
                csvPrinter.printRecord(entry.getKey(), Math.round(entry.getValue() * 1000.0) / 1000.0);
        }));

        printFeatureCorrGP(featureCorrelation);
        printFeatureCorrGC(featureCorrelation);

        printFeatureLocCorrG(featureLinesOfCodeCorrelation);
        printFeatureLocCorrGF(featureLinesOfCodeCorrelation);

        printFeatureCommentCorrG(featureCommentCorrelation);
        printFeatureCommentCorrGF(featureCommentCorrelation);
    }

    private static void printFeatureCorrGP(FeatureCorrelation featureCorrelation) {
        var featureCorrGP = featureCorrelation.calcCorrelationGloballyPresenceBased();

        var headers = new String[featureCorrGP.size() + 1];
        headers[0] = "feature";

        var features = featureCorrGP.keySet();
        System.arraycopy(features.toArray(String[]::new), 0, headers, 1, features.size());

        printCSV(headers, "metrics/featureCorrelationGloballyPresenceBased.csv", (csvPrinter -> {
            for (var f1 : features) {
                var values = features.stream().map(f2 -> {
                    var corrP = featureCorrGP.get(f1).get(f2);
                    return corrP.b < 0.05 ? corrP.a : Double.NaN;
                }).toArray(Double[]::new);

                var row = new Object[values.length + 1];
                row[0] = f1;
                System.arraycopy(values, 0, row, 1, values.length);

                csvPrinter.printRecord(row);
            }
        }));
    }

    private static void printFeatureCorrGC(FeatureCorrelation featureCorrelation) {
        var featureCorrGC = featureCorrelation.calcCorrelationGloballyCountBased();

        var headers = new String[featureCorrGC.size() + 1];
        headers[0] = "feature";

        var features = featureCorrGC.keySet();
        System.arraycopy(features.toArray(String[]::new), 0, headers, 1, features.size());

        printCSV(headers, "metrics/featureCorrelationGloballyCountBased.csv", (csvPrinter -> {
            for (var f1 : features) {
                var values = features.stream().map(f2 -> {
                    var corrP = featureCorrGC.get(f1).get(f2);
                    return corrP.b < 0.05 ? corrP.a : Double.NaN;
                }).toArray(Double[]::new);

                var row = new Object[values.length + 1];
                row[0] = f1;
                System.arraycopy(values, 0, row, 1, values.length);

                csvPrinter.printRecord(row);
            }
        }));
    }

    private static void printFeatureLocCorrG(FeatureLinesOfCodeCorrelation featureLinesOfCodeCorrelation) {
        var featureLocCorrG = featureLinesOfCodeCorrelation.calcCorrelationGlobally();

        printCSV(new String[]{"correlation", "p_value"}, "metrics/featureLinesOfCodeCorrelationGlobally.csv", (csvPrinter -> {
            csvPrinter.printRecord(featureLocCorrG.a, featureLocCorrG.b);
        }));
    }

    private static void printFeatureLocCorrGF(FeatureLinesOfCodeCorrelation featureLinesOfCodeCorrelation) {
        var featureLocCorrGF = featureLinesOfCodeCorrelation.calcCorrelationGloballyPerFeature();

        var headers = new String[]{"feature", "correlation", "p_value"};
        var features = featureLocCorrGF.keySet();

        printCSV(headers, "metrics/featureLinesOfCodeCorrelationGloballyPerFeature.csv", (csvPrinter -> {
            for (var feature : features) {
                var corrP = featureLocCorrGF.get(feature);
                csvPrinter.printRecord(feature, corrP.a, corrP.b);
            }
        }));
    }

    private static void printFeatureCommentCorrG(FeatureCommentCorrelation featureCommentCorrelation) {
        var featureCommentCorrG = featureCommentCorrelation.calcCorrelationGlobally();

        printCSV(new String[]{"correlation", "p_value"}, "metrics/featureCommentCorrelationGloballySpearman.csv", (csvPrinter -> {
            csvPrinter.printRecord(featureCommentCorrG.a, featureCommentCorrG.b);
        }));
    }

    private static void printFeatureCommentCorrGF(FeatureCommentCorrelation featureCommentCorrelation) {
        var featureCommentCorrGF = featureCommentCorrelation.calcCorrelationGloballyPerFeature();

        var headers = new String[]{"feature", "correlation", "p_value"};
        var features = featureCommentCorrGF.keySet();

        printCSV(headers, "metrics/featureCommentCorrelationGloballyPerFeatureSpearman.csv", (csvPrinter -> {
            for (var feature : features) {
                var corrP = featureCommentCorrGF.get(feature);
                csvPrinter.printRecord(feature, corrP.a, corrP.b);
            }
        }));
    }

    private static void printCSV(String[] headers, String path, CSVFunction print) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            var format = CSVFormat.DEFAULT.builder().setHeader(headers).build();
            var csvPrinter = new CSVPrinter(writer, format);

            print.apply(csvPrinter);

            csvPrinter.flush();
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
        }
    }
}