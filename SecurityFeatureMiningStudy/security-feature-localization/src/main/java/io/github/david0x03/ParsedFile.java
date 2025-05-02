package io.github.david0x03;

import com.google.gson.stream.JsonWriter;
import io.github.david0x03.mappings.ApiMappings;
import io.github.david0x03.project.JavaProject;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Represents a parsed file where all security features have been extracted.
 * This class tracks API calls, unresolved bindings, and provides functionality
 * to annotate the source code with extracted security features.
 */
public class ParsedFile {

    private static final Logger logger = LogManager.getLogger(ParsedFile.class);

    private final Path filePath;
    private final ApiMappings apiMappings;

    private final List<ApiCall> apiCalls = new ArrayList<>();
    private final List<MissingBinding> missingBindings = new ArrayList<>();

    /**
     * Constructs a ParsedFile object by parsing the specified file and extracting features.
     *
     * @param filePath    The path of the file being parsed.
     * @param cu          The compilation unit of the file.
     * @param apiMappings The API mappings used for feature extraction.
     */
    public ParsedFile(Path filePath, CompilationUnit cu, ApiMappings apiMappings) {
        this.apiMappings = apiMappings;
        this.filePath = filePath;

        cu.accept(new AstVisitor(this));
    }

    /**
     * Adds an API call to the parsed file.
     *
     * @param node          The AST node representing the API call.
     * @param qualifiedName The fully qualified name of the API endpoint.
     * @param apiCallType   The type of the API call.
     */
    public void addApiCall(ASTNode node, String qualifiedName, ApiCall.APICallType apiCallType) {
        var cleanQualifiedName = qualifiedName;

        // Removes any generics from the qualified name
        // Example: namespace.print<T> -> namespace.print
        var genericsRegex = "<[^<>]*>";
        while (cleanQualifiedName.matches(".*<[^<>]*>.*")) {
            cleanQualifiedName = cleanQualifiedName.replaceAll(genericsRegex, "");
        }

        var features = apiMappings.getCategories(cleanQualifiedName);
        if (features == null) return;

        ApiCall apiCall = new ApiCall(cleanQualifiedName, node, apiCallType, features);
        apiCalls.add(apiCall);
    }

    /**
     * Adds a missing binding to the parsed file.
     *
     * @param missingBinding The unresolved binding to be recorded.
     */
    public void addMissingBinding(MissingBinding missingBinding) {
        this.missingBindings.add(missingBinding);
    }

    /**
     * Annotates the source code with comments representing the extracted security features.
     *
     * @return The source code with HAnS annotations, or null if the file cannot be read.
     */
    public String getAnnotateSourceCode() {
        // Maps all security features to the lines, they occur at
        var linesToFeatureMap = getLinesToFeaturesMap();

        String sourceCode;
        try {
            sourceCode = getSourceCode();
        } catch (IOException e) {
            logger.error("Failed to access the file: ", e);
            return null;
        }

        // Split the sourcecode in lines
        var lines = new ArrayList<>(List.of(sourceCode.split("\r?\n")));

        // Track the offset introduced by the added lines from the annotation comments
        int offset = 0;
        for (var lineToFeatures : linesToFeatureMap) {
            var line = lineToFeatures.line + offset;

            // Concatenate the features together with commas e.g. Feature1, Feature2, ...
            var features = String.join(", ", lineToFeatures.features());

            // Detect the indentation and create the annotation comment
            var indentation = lines.get(line).replaceFirst("^(\\s*).*", "$1");
            var beginAnnotation = indentation + "// &begin[" + features + "]";
            var endAnnotation = indentation + "// &end[" + features + "]";

            // Insert the comment and increase the offset by 2 (begin & end comments)
            lines.add(line, beginAnnotation);
            lines.add(line + 2, endAnnotation);
            offset += 2;
        }

        return String.join("\n", lines);
    }

    /**
     * Writes the annotated source code back to the file.
     *
     * @throws IOException If an error occurs while writing to the file.
     */
    public void writeAnnotatedSourceCode() throws IOException {
        Files.write(filePath, getAnnotateSourceCode().getBytes());
    }

    /**
     * Creates a mapping of line numbers to security features.
     *
     * @return A list of lines and their associated security features.
     */
    private List<LineToFeatures> getLinesToFeaturesMap() {
        List<LineToFeatures> linesToFeaturesMap = new ArrayList<>();

        apiCalls.forEach(apiCall -> {
            int line = apiCall.start.line();
            var features = new HashSet<>(apiCall.getFeatures());

            // Check if the line already exists in the list
            Optional<LineToFeatures> existingLtfOpt = linesToFeaturesMap.stream()
                    .filter(ltf -> ltf.line() == line)
                    .findFirst();

            if (existingLtfOpt.isPresent()) {
                // Merge features if the line number already exists in the list
                var ltf = existingLtfOpt.get();
                ltf.features().addAll(features);
            } else {
                // Add a new pair if the line number doesn't exist
                linesToFeaturesMap.add(new LineToFeatures(line, features));
            }
        });

        return linesToFeaturesMap;
    }

    /**
     * Retrieves the source code of the parsed file.
     *
     * @return The source code as a string.
     * @throws IOException If the file cannot be read.
     */
    public String getSourceCode() throws IOException {
        return Files.readString(filePath);
    }

    /**
     * Retrieves all API calls recorded in the parsed file.
     *
     * @return A list of API calls.
     */
    public List<ApiCall> getApiCalls() {
        return apiCalls;
    }

    /**
     * Retrieves all missing bindings recorded in the parsed file.
     *
     * @return A list of missing bindings.
     */
    public List<MissingBinding> getMissingBindings() {
        return missingBindings;
    }

    /**
     * Retrieves the absolute path of the parsed file.
     *
     * @return The absolute file path.
     */
    public Path getFilePath() {
        return filePath.toAbsolutePath();
    }

    /**
     * Writes the parsed file details to a JSON writer.
     *
     * @param jsonWriter The JsonWriter used for output.
     * @param project    The project associated with the parsed file.
     * @throws IOException If an error occurs during writing.
     */
    public void writeJson(JsonWriter jsonWriter, JavaProject project) throws IOException {
        jsonWriter.beginObject();

        var relPath = project.getProjectPath().relativize(filePath);
        jsonWriter.name("path").value(relPath.toString());

        jsonWriter.name("apiCalls").beginArray();
        for (var apiCall : apiCalls) apiCall.writeJson(jsonWriter);
        jsonWriter.endArray();

        jsonWriter.name("missingBindings").value(missingBindings.size());

        jsonWriter.endObject();
    }

    /**
     * A helper record to map features to a specific line.
     */
    private record LineToFeatures(int line, Set<String> features) {
        @Override
        public String toString() {
            return "Line: " + line + " | " + String.join(", ", features);
        }
    }
}
