package io.github.david0x03.metrics;

import io.github.david0x03.ParsedFile;

/**
 * Represents metrics for a single file, including:
 * - Total lines
 * - Logical lines of code
 * - Commented lines
 */
public class FileMetrics {

    private final ParsedFile pf;

    private int lines = -1;
    private int linesOfCode = -1;
    private int commentedLines = -1;

    /**
     * Initializes the FileMetrics object with a parsed file.
     *
     * @param pf The {@link ParsedFile} for which metrics are calculated.
     */
    public FileMetrics(ParsedFile pf) {
        this.pf = pf;
    }

    public ParsedFile getParsedFile() {
        return pf;
    }

    public int getLines() {
        return lines;
    }

    public void setLines(int lines) {
        this.lines = lines;
    }

    public int getLinesOfCode() {
        return linesOfCode;
    }

    public void setLinesOfCode(int linesOfCode) {
        this.linesOfCode = linesOfCode;
    }

    public int getCommentedLines() {
        return commentedLines;
    }

    public void setCommentedLines(int commentedLines) {
        this.commentedLines = commentedLines;
    }

    @Override
    public String toString() {
        var s = pf.getFilePath() + "\n";
        s += "\tLines: " + lines + "\n";
        s += "\tLOC: " + linesOfCode + "\n";
        s += "\tCL: " + commentedLines;
        return s;
    }
}
