package io.github.david0x03;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.google.gson.stream.JsonWriter;

import io.github.david0x03.mappings.ApiMappings;
import io.github.david0x03.project.JavaProject;

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
	 * Constructs a ParsedFile object by parsing the specified file and extracting
	 * features.
	 *
	 * @param filePath    The path of the file being parsed.
	 * @param cu          The compilation unit of the file.
	 * @param apiMappings The API mappings used for feature extraction.
	 */
	public ParsedFile(final Path filePath, final CompilationUnit cu, final ApiMappings apiMappings) {
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
	public void addApiCall(final ASTNode node, final String qualifiedName, final ApiCall.APICallType apiCallType) {
		var cleanQualifiedName = qualifiedName;

		// Removes any generics from the qualified name
		// Example: namespace.print<T> -> namespace.print
		final var genericsRegex = "<[^<>]*>";
		while (cleanQualifiedName.matches(".*<[^<>]*>.*")) {
			cleanQualifiedName = cleanQualifiedName.replaceAll(genericsRegex, "");
		}

		final var features = this.apiMappings.getCategories(cleanQualifiedName);
		if (features == null) {
			return;
		}

		final var apiCall = new ApiCall(cleanQualifiedName, node, apiCallType, features);
		this.apiCalls.add(apiCall);
	}

	/**
	 * Adds a missing binding to the parsed file.
	 *
	 * @param missingBinding The unresolved binding to be recorded.
	 */
	public void addMissingBinding(final MissingBinding missingBinding) {
		this.missingBindings.add(missingBinding);
	}

	/**
	 * Annotates the source code with comments representing the extracted security
	 * features.
	 *
	 * @return The source code with HAnS annotations, or null if the file cannot be
	 *         read.
	 */
	public String getAnnotateSourceCode() {
		// Maps all security features to the lines, they occur at
		final var linesToFeatureMap = this.getLinesToFeaturesMap();

		String sourceCode;
		try {
			sourceCode = this.getSourceCode();
		} catch (final IOException e) {
			logger.error("Failed to access the file: ", e);
			return null;
		}

		// Split the sourcecode in lines
		final var lines = new ArrayList<>(List.of(sourceCode.split("\r?\n")));

		// Track the offset introduced by the added lines from the annotation comments
		var offset = 0;
		for (final var lineToFeatures : linesToFeatureMap) {
			final var line = lineToFeatures.line + offset;

			// Concatenate the features together with commas e.g. Feature1, Feature2, ...
			final var features = String.join(", ", lineToFeatures.features());

			// Detect the indentation and create the annotation comment
			final var indentation = lines.get(line).replaceFirst("^(\\s*).*", "$1");
			final var beginAnnotation = indentation + "// &begin[" + features + "]";
			final var endAnnotation = indentation + "// &end[" + features + "]";

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
		Files.write(this.filePath, this.getAnnotateSourceCode().getBytes());
	}

	/**
	 * Creates a mapping of line numbers to security features.
	 *
	 * @return A list of lines and their associated security features.
	 */
	private List<LineToFeatures> getLinesToFeaturesMap() {
		final List<LineToFeatures> linesToFeaturesMap = new ArrayList<>();

		this.apiCalls.forEach(apiCall -> {
			final var line = apiCall.start.line();
			final var features = new HashSet<>(apiCall.getFeatures());

			// Check if the line already exists in the list
			final var existingLtfOpt = linesToFeaturesMap.stream()
					.filter(ltf -> ltf.line() == line)
					.findFirst();

			if (existingLtfOpt.isPresent()) {
				// Merge features if the line number already exists in the list
				final var ltf = existingLtfOpt.get();
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
		return Files.readString(this.filePath);
	}

	/**
	 * Retrieves all API calls recorded in the parsed file.
	 *
	 * @return A list of API calls.
	 */
	public List<ApiCall> getApiCalls() {
		return this.apiCalls;
	}

	/**
	 * Retrieves all missing bindings recorded in the parsed file.
	 *
	 * @return A list of missing bindings.
	 */
	public List<MissingBinding> getMissingBindings() {
		return this.missingBindings;
	}

	/**
	 * Retrieves the absolute path of the parsed file.
	 *
	 * @return The absolute file path.
	 */
	public Path getFilePath() {
		return this.filePath.toAbsolutePath();
	}

	/**
	 * Writes the parsed file details to a JSON writer.
	 *
	 * @param jsonWriter The JsonWriter used for output.
	 * @param project    The project associated with the parsed file.
	 * @throws IOException If an error occurs during writing.
	 */
	public void writeJson(final JsonWriter jsonWriter, final JavaProject project) throws IOException {
		jsonWriter.beginObject();

		final var relPath = project.getProjectPath().relativize(this.filePath);
		jsonWriter.name("path").value(relPath.toString());

		jsonWriter.name("apiCalls").beginArray();
		for (final var apiCall : this.apiCalls) {
			apiCall.writeJson(jsonWriter);
		}
		jsonWriter.endArray();

		jsonWriter.name("missingBindings").value(this.missingBindings.size());

		jsonWriter.endObject();
	}

	/**
	 * A helper record to map features to a specific line.
	 */
	private record LineToFeatures(int line, Set<String> features) {
		@Override
		public String toString() {
			return "Line: " + this.line + " | " + String.join(", ", this.features);
		}
	}
}
