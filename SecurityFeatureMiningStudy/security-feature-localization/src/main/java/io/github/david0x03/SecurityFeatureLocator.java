package io.github.david0x03;

import com.google.gson.stream.JsonWriter;
import io.github.david0x03.mappings.ApiMappings;
import io.github.david0x03.project.JavaProject;
import io.github.david0x03.project.JavaSource;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Responsible for locating security features in Java projects by parsing source files and analyzing
 * API usage. Supports Maven and Gradle-based projects.
 */
public class SecurityFeatureLocator {

    private final Logger logger = LogManager.getLogger(SecurityFeatureLocator.class);

    private final ApiMappings apiMappings;

    /**
     * Initializes the SecurityFeatureLocator with API mappings.
     *
     * @param mappingsDir The directory containing API mappings.
     */
    public SecurityFeatureLocator(Path mappingsDir) {
        var apiMappings = new ApiMappings();

        try {
            apiMappings.addMappingsFromDir(mappingsDir);
        } catch (Exception e) {
            logger.error("Mappings could not be read: ", e);
            System.exit(1);
        }

        this.apiMappings = apiMappings;
    }

    /**
     * Locates security features in a given project by analyzing its source files.
     *
     * @param projectDir       The path to the project directory. Must be a Maven or Gradle project.
     * @param createJsonExport Whether to generate a JSON export of the located features.
     * @return A list of parsed files with identified security features.
     * @throws Exception If the project cannot be loaded or processed.
     */
    public JavaProject locateFeatures(String projectDir, boolean createJsonExport) throws Exception {
        // Load the project
        var project = JavaProject.load(Paths.get(projectDir).toAbsolutePath());

        // Locate all sources
        var sources = project.getSources();

        var parsedFiles = new ArrayList<ParsedFile>();

        logger.info("Project: " + project.getProjectPath());
        logger.info("Found " + sources.size() + " source(s)");

        // Extract the java version
        logger.info("Extracting Java version...");
        String javaVersion = project.getJavaSourceVersion();
        if (javaVersion != null) logger.info("Found version: " + javaVersion);
        else logger.info("Unable to extract Java version, using fallback");

        // Build the project
        logger.info("Building project...");
        var buildSuccess = project.buildProject();
        if (buildSuccess) logger.info("Build successful");
        else logger.info("Build failed, continuing");

        for (var source : sources) {
            logger.info("Extracting security features from: " + source.getRelativeSourcePath());
            parsedFiles.addAll(parseSourceDir(source));
        }

        project.setParsedFiles(parsedFiles);

        if (createJsonExport) {
            try {
                logger.info("Creating JSON files");
                createJsonExport(project, parsedFiles);
            } catch (IOException e) {
                logger.error("Failed to create json file: ", e);
            }
        }

        logger.info("Done");
        return project;
    }

    /**
     * Parses a single source directory to identify security features using AST analysis.
     *
     * @param source The Java source to be parsed.
     * @return A list of parsed files with identified security features.
     */
    private List<ParsedFile> parseSourceDir(JavaSource source) {
        // Configure the AST parser
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);

        // Use a fallback of Java 1.8
        var javaSourceVersion = source.getProject().getJavaSourceVersion();
        if (javaSourceVersion == null) javaSourceVersion = JavaCore.VERSION_1_8;

        // Set the java version
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(javaSourceVersion, options);
        parser.setCompilerOptions(options);

        // Get the dependencies
        var dependencies = source.getDependencies().stream().map(Path::toString).toArray(String[]::new);

        // Pass all other sources to the AST Parser
        var sourcePaths = source.getProject().getSources().stream().map(s -> s.getSourcePath().toString()).toArray(String[]::new);

        parser.setEnvironment(dependencies, sourcePaths, null, true);

        var parsedFiles = new ArrayList<ParsedFile>();
        FileASTRequestor requestor = new FileASTRequestor() {
            @Override
            public void acceptAST(String source, CompilationUnit cu) {
                var pf = new ParsedFile(Paths.get(source), cu, apiMappings);
                parsedFiles.add(pf);
            }
        };

        // Get java files from the source
        var javaFiles = source.getJavaFiles();
        javaFiles.addAll(source.getGeneratedFiles());

        // Parse the files
        var sources = javaFiles.toArray(String[]::new);
        parser.createASTs(sources, null, new String[0], requestor, new NullProgressMonitor());

        return parsedFiles;
    }

    /**
     * Creates a JSON export file containing all located security features.
     *
     * @param project     The Java project being analyzed.
     * @param parsedFiles The list of parsed files with identified security features.
     * @throws IOException If the JSON file cannot be created or written.
     */
    private void createJsonExport(JavaProject project, List<ParsedFile> parsedFiles) throws IOException {
        String javaVersion = project.getJavaSourceVersion();
        var buildSuccess = project.buildProject();
        var sources = project.getSources();

        var exportPath = project.getProjectPath().resolve("result/features.json");
        FileUtils.createParentDirectories(exportPath.toFile());

        var jsonWriter = new JsonWriter(new BufferedWriter(new FileWriter(exportPath.toFile())));
        jsonWriter.setIndent("  ");

        jsonWriter.beginObject();
        jsonWriter.name("javaVersion").value(javaVersion);
        jsonWriter.name("buildSuccess").value(buildSuccess);

        jsonWriter.name("sources").beginArray();
        for (var source : sources) {
            jsonWriter.beginObject();
            jsonWriter.name("path").value(source.getRelativeSourcePath().toString());
            jsonWriter.name("files").beginArray();
            for (var pf : parsedFiles) pf.writeJson(jsonWriter, project);
            jsonWriter.endArray();
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.endObject();
        jsonWriter.flush();
    }
}
