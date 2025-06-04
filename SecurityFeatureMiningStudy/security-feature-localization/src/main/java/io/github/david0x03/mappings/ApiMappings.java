package io.github.david0x03.mappings;

import com.google.gson.Gson;
import io.github.david0x03.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Handles the management and loading of API mappings for identifying categories of API endpoints.
 * Mappings are loaded from JSON files and stored for efficient lookup.
 */
public class ApiMappings {

    protected static final Logger logger = LogManager.getLogger(ApiMappings.class);

    private final List<Library> libraries = new ArrayList<>();
    public Map<String, List<String>> mappings = new HashMap<>();

    /**
     * Retrieves the categories associated with a given API endpoint identifier.
     *
     * @param identifier The API endpoint identifier to search for.
     * @return A list of categories assigned to the identifier, or null if no categories are found.
     */
    public List<String> getCategories(String identifier) {
        // Go through all libraries and check if it matches any
        for (var library : libraries) {
            var res = library.matchIdentifier(identifier);
            if (res != null && res.length > 0) return Arrays.asList(res);
        }

        // Endpoint isn't mapped
        return null;
    }

    /**
     * Loads API mappings from a specified directory containing JSON files.
     *
     * @param dirPath Path to the directory containing mapping files.
     * @throws Exception If the directory does not exist or is not a valid directory.
     */
    public void addMappingsFromDir(Path dirPath) throws Exception {
        if (!Files.isDirectory(dirPath))
            throw new Exception("Mapping directory not found");

        // Collect all json files in the directory
        var mappingFiles = new ArrayList<String>();
        Utils.collectFiles(new File(dirPath.toString()), ".json", mappingFiles);

        // Add all mappings
        for (var file : mappingFiles)
            this.addMappingsFromFile(Paths.get(file));
    }

    /**
     * Loads a single mapping file into the API mappings.
     *
     * @param filePath Path to the JSON mapping file.
     */
    public void addMappingsFromFile(Path filePath) {
        if (!Files.isRegularFile(filePath) || !filePath.toString().endsWith(".json")) {
            logger.error("Invalid mapping file: " + filePath);
            return;
        }

        try {
            var json = Files.readString(filePath);
            var lib = new Gson().fromJson(json, Library.class);
            libraries.add(lib);
        } catch (IOException e) {
            logger.error("Invalid mapping file: " + filePath);
        }
    }
}