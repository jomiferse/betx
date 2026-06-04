package com.betx;

import com.betx.cli.BetxRootCommand;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import picocli.CommandLine;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BetxApplication implements CommandLineRunner, ExitCodeGenerator {
    private final CommandLine.IFactory factory;
    private final BetxRootCommand rootCommand;
    private int exitCode;

    public BetxApplication(CommandLine.IFactory factory, BetxRootCommand rootCommand) {
        this.factory = factory;
        this.rootCommand = rootCommand;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BetxApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.setDefaultProperties(Map.of(
            "spring.main.log-startup-info", "false",
            "logging.level.root", "OFF"
        ));
        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(rootCommand, factory)
            .setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
                commandLine.getErr().println(exception.getMessage());
                return 1;
            })
            .execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Bean
    CommandLine.IFactory picocliFactory(org.springframework.context.ApplicationContext context) {
        return new picocli.spring.PicocliSpringFactory(context);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
