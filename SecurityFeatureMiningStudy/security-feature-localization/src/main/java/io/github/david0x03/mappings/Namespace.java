package io.github.david0x03.mappings;

/**
 * Represents a namespace in a hierarchical API mapping structure. Each namespace can
 * contain child namespaces and associated categories, allowing for recursive matching
 * of API endpoint identifiers.
 */
public class Namespace {

    private String namespace;
    private String[] categories;

    @SuppressWarnings("all")
    private Namespace[] children;

    /**
     * Recursively matches an API endpoint identifier against the namespace hierarchy.
     *
     * @param identifier The identifier of the API endpoint to match.
     * @param namespace  The currently matched namespace as the recursion progresses.
     * @return An array of categories associated with the identifier, or null if no match is found.
     */
    protected String[] matchIdentifier(String identifier, String namespace) {
        var currentNamespace = namespace + (namespace.isEmpty() ? "" : ".") + this.namespace;

        // Return null if the identifier does not match the current namespace prefix
        if (!identifier.startsWith(currentNamespace)) return null;

        // Return null if the identifier does not match the current namespace prefix
        //noinspection RedundantLengthCheck
        if (identifier.equals(currentNamespace) || children.length == 0)
            return categories;

        // Explore child namespaces recursively
        for (Namespace child : children) {
            var res = child.matchIdentifier(identifier, currentNamespace);
            if (res != null) return res;
        }

        // Return categories if the identifier matches this namespace but no children match
        return categories;
    }
}
