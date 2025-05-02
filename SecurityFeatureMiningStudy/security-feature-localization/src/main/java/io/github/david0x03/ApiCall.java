package io.github.david0x03;

import com.google.gson.stream.JsonWriter;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.util.List;

/**
 * Represents an API call in the source code.
 * Tracks the qualified name, API call type, source code position, and associated features.
 */
public class ApiCall {

    String qualifiedName;
    ApiCall.APICallType apiCallType;

    Position start;
    Position end;

    List<String> features;

    /**
     * Constructs an ApiCall instance with details about the API call.
     *
     * @param qualifiedName The fully qualified name of the API being called.
     * @param node          The ASTNode representing the API call in the source code.
     * @param apiCallType   The type of the API call (e.g., method invocation, field access).
     * @param features      A list of features associated with the API call.
     */
    public ApiCall(String qualifiedName, ASTNode node, ApiCall.APICallType apiCallType, List<String> features) {
        this.qualifiedName = qualifiedName;
        this.apiCallType = apiCallType;

        this.start = Position.fromIndex(node, node.getStartPosition());
        this.end = Position.fromIndex(node, node.getStartPosition() + node.getLength());

        this.features = features;
    }

    /**
     * Retrieves the features associated with the API call.
     *
     * @return A list of features.
     */
    public List<String> getFeatures() {
        return features;
    }

    @Override
    public String toString() {
        return "Line: " + start.line + " | API: " + qualifiedName + " | Features: " + String.join(", ", features);
    }

    /**
     * Writes the API call details to a JSON writer.
     *
     * @param jsonWriter The JsonWriter to write the API call information to.
     * @throws IOException If an error occurs during writing.
     */
    public void writeJson(JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();

        jsonWriter.name("line").value(start.line);
        jsonWriter.name("api").value(qualifiedName);

        jsonWriter.name("features").beginArray();
        for (String feature : features) jsonWriter.value(feature);
        jsonWriter.endArray();

        jsonWriter.endObject();
    }

    public enum APICallType {
        ClassInstanceCreation,
        MethodInvocation,
        FieldAccess,
        Annotation,
    }

    /**
     * Represents a position in the source code, defined by line and column numbers.
     */
    public record Position(int line, int column) {

        /**
         * Extracts the position (start or end) of an ASTNode in the source code.
         *
         * @param node  The ASTNode to extract the position from.
         * @param index The index (start or end position) within the node.
         * @return A Position object representing the line and column of the index.
         */
        public static Position fromIndex(ASTNode node, int index) {
            var cu = (CompilationUnit) node.getRoot();
            var line = cu.getLineNumber(index) - 1;
            var column = cu.getColumnNumber(index);

            return new ApiCall.Position(line, column);
        }

        @Override
        public String toString() {
            return line + ":" + column;
        }
    }
}
