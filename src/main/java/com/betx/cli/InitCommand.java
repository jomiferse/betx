package com.betx.cli;

import com.betx.application.InitializeProjectService;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "init", description = "Create starter config files and local data directories.")
public class InitCommand implements Runnable {
    private final InitializeProjectService initializeProjectService;
    private final Path baseDirectory;

    @Option(names = "--force", description = "Overwrite template files.")
    boolean force;

    @Autowired
    public InitCommand(InitializeProjectService initializeProjectService) {
        this(initializeProjectService, Path.of("."));
    }

    InitCommand(InitializeProjectService initializeProjectService, Path baseDirectory) {
        this.initializeProjectService = initializeProjectService;
        this.baseDirectory = baseDirectory;
    }

    @Override
    public void run() {
        var result = initializeProjectService.initialize(baseDirectory, force);

        System.out.println("BetX project files are ready.");
        if (result.configWritten()) {
            System.out.println("  - betx.yml");
        }
        System.out.println("  - data/");
        System.out.println("  - models/");
    }
}
