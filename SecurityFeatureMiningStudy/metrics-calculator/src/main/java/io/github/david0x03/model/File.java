package io.github.david0x03.model;

public record File(long id, long minedRepoId, String path, int lines, int linesOfCode, int commentLines,
                   int missingBindings) {
}
