package io.github.david0x03;

import org.eclipse.jdt.core.dom.ASTNode;

import java.nio.file.Path;

/**
 * Represents a missing binding in the source code.
 * This occurs when an API call's binding cannot be resolved during AST traversal.
 *
 * @param type     The type of API call for which the binding could not be identified.
 * @param node     The AST node where binding resolution failed.
 * @param filePath The path of the file containing the unresolved node.
 */
public record MissingBinding(ApiCall.APICallType type, ASTNode node, Path filePath) {
    @Override
    public String toString() {
        var pos = ApiCall.Position.fromIndex(node, node.getStartPosition());

        var s = "Missing binding (" + type.name() + ")\n";
        s += ("\t" + filePath + " (" + pos + ")\n");
        s += "\t" + node.toString().replace("\n", "\n\t");

        return s;
    }
}
