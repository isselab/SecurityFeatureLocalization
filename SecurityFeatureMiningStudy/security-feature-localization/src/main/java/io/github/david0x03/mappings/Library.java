package io.github.david0x03.mappings;

/**
 * Represents a mapped library, including its name and version, and provides functionality
 * to match API endpoint identifiers to their respective categories within the mapping structure.
 */
public class Library extends Namespace {

    public String name, version;

    /**
     * Matches an API endpoint identifier against the library's mapping structure.
     * This operation is performed recursively.
     *
     * @param identifier The identifier of the API endpoint to match.
     * @return An array of categories associated with the identifier, or null if no match is found.
     */
    public String[] matchIdentifier(String identifier) {
        // Implementation provided by the Namespace class
        return this.matchIdentifier(identifier, "");
    }
}
