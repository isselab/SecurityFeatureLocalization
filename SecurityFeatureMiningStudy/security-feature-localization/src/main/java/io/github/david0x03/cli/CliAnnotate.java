package io.github.david0x03.cli;

import io.github.david0x03.SecurityFeatureLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.nio.file.Paths;

@CommandLine.Command(
        name = "annotate", description = "TODO"
)
public class CliAnnotate implements Runnable {

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
            var securityFeatureLocator = new SecurityFeatureLocator(mappingsDir);
            var project = securityFeatureLocator.locateFeatures(projectPath, false);

            // Insert annotations
            for (var pf : project.getParsedFiles()) pf.writeAnnotatedSourceCode();
        } catch (Exception e) {
            logger.error("Failed to extract features: ", e);
            System.exit(1);
        }
    }
}