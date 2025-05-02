package io.github.david0x03;

import io.github.david0x03.cli.CliAnnotate;
import io.github.david0x03.cli.CliLocate;
import picocli.CommandLine;

@CommandLine.Command(
        name = "SecurityFeatureLocator",
        description = "TODO",
        mixinStandardHelpOptions = true,
        version = "SecurityFeatureLocator 1.0.0",
        subcommands = {CliLocate.class, CliAnnotate.class}
)

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}