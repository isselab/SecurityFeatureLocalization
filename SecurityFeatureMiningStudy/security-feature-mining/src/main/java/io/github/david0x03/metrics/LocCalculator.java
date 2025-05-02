package io.github.david0x03.metrics;

import io.github.david0x03.project.JavaProject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates lines of code metrics for files in a Java project.
 * Metrics include total lines, logical lines of code, and commented lines.
 */
public class LocCalculator {

    private final List<FileMetrics> fileMetrics = new ArrayList<>();

    /**
     * Initializes the LOC calculator and computes metrics for all parsed files in the given project.
     *
     * @param project The {@link JavaProject} containing parsed files for which metrics will be calculated.
     */
    public LocCalculator(JavaProject project) {
        project.getParsedFiles().forEach(pf ->
                fileMetrics.add(new FileMetrics(pf))
        );

        calcLinesOfCode();
    }

    /**
     * Calculates lines of code metrics for all files.
     */
    private void calcLinesOfCode() {
        fileMetrics.forEach(this::calcBasicLineMetricsFromFile);
    }

    /**
     * Computes line metrics for a single file, including:
     * - Total lines
     * - Logical ines of code
     * - Commented lines
     *
     * @param fileMetrics The {@link FileMetrics} object representing the file being analyzed.
     */
    private void calcBasicLineMetricsFromFile(FileMetrics fileMetrics) {
        int lines = 0;
        int linesOfCode = 0;
        int commentedLines = 0;

        boolean insideBlockComment = false;

        var filePath = fileMetrics.getParsedFile().getFilePath().toAbsolutePath().toString();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var trimmedLine = line.trim();
                lines++;

                if (trimmedLine.endsWith("*/")) {
                    insideBlockComment = false;
                    commentedLines++;
                    continue;
                }

                if (trimmedLine.startsWith("//") || insideBlockComment) {
                    commentedLines++;
                    continue;
                }

                if (trimmedLine.isEmpty()) continue;

                if (trimmedLine.startsWith("/*")) {
                    insideBlockComment = true;
                    commentedLines++;
                    continue;
                }

                linesOfCode++;
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        fileMetrics.setLines(lines);
        fileMetrics.setLinesOfCode(linesOfCode);
        fileMetrics.setCommentedLines(commentedLines);
    }

    /**
     * Retrieves the list of file metrics for the analyzed project.
     *
     * @return A list of {@link FileMetrics} objects containing metrics for each file.
     */
    public List<FileMetrics> getFileMetrics() {
        return this.fileMetrics;
    }
}
