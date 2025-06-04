package io.github.david0x03.cli;

import io.github.david0x03.SecurityFeatureLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.nio.file.Paths;

@CommandLine.Command(
        name = "locate",
        description = "TODO"
)
public class CliLocate implements Runnable {

    private static final Logger logger = LogManager.getLogger(CliLocate.class);

    @CommandLine.Parameters(
            index = "0",
            description = "TODO"
    )
    String projectPath;

    @CommandLine.Option(
            names = {"--mappings"},
            required = true,
            description = "TODO"
    )
    String mappingsPath;

    @Override
    public void run() {
        var mappingsDir = Paths.get(mappingsPath).toAbsolutePath();

        try {
            new SecurityFeatureLocator(mappingsDir).locateFeatures(projectPath, true);
        } catch (Exception e) {
            logger.error("Failed to extract features: ", e);
            System.exit(1);
        }
    }
}