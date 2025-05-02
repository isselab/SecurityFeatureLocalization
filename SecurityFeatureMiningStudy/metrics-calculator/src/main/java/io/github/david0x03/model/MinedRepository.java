package io.github.david0x03.model;

public record MinedRepository(long id, long repoId, String javaVersion, boolean buildSuccess, String note) {
}
