package com.betx;

import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLogger;
import com.betx.cli.BetxRootCommand;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import picocli.CommandLine;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BetxApplication implements CommandLineRunner, ExitCodeGenerator {
    private final CommandLine.IFactory factory;
    private final BetxRootCommand rootCommand;
    private final Environment environment;
    private final BetxEventLogger eventLogger;
    private int exitCode;

    public BetxApplication(
        CommandLine.IFactory factory,
        BetxRootCommand rootCommand,
        Environment environment,
        BetxEventLogger eventLogger
    ) {
        this.factory = factory;
        this.rootCommand = rootCommand;
        this.environment = environment;
        this.eventLogger = eventLogger;
    }

    public static void main(String[] args) {
        System.exit(runForExitCode(args));
    }

    static int runForExitCode(String[] args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        installCliLogging(args, originalOut, originalErr);
        SpringApplication app = new SpringApplication(BetxApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.setDefaultProperties(Map.of(
            "spring.main.log-startup-info", "false",
            "logging.level.root", "OFF"
        ));
        try (ConfigurableApplicationContext context = app.run(args)) {
            return context.getBean(BetxApplication.class).getExitCode();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static void installCliLogging(String[] args, PrintStream originalOut, PrintStream originalErr) {
        CliLogConfig cliLogConfig = CliLogConfig.fromArgs(args);
        if (!cliLogConfig.enabled()) {
            return;
        }
        CliLogWriter writer = new CliLogWriter(cliLogConfig.directory(), Clock.systemDefaultZone());
        System.setOut(writer.loggingPrintStream(originalOut));
        System.setErr(writer.loggingPrintStream(originalErr));
    }

    @Override
    public void run(String... args) {
        eventLogger.info(BetxEventCategory.OPERATIONAL, "app.started")
            .correlationId("app-" + java.time.Instant.now())
            .result("started")
            .field("command", commandName(args))
            .emit();
        if (environment.getProperty("betx.interface.enabled", Boolean.class, false)) {
            exitCode = 0;
            eventLogger.info(BetxEventCategory.OPERATIONAL, "app.stopped")
                .result("stopped")
                .field("exitCode", exitCode)
                .emit();
            return;
        }
        exitCode = new CommandLine(rootCommand, factory)
            .setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
                eventLogger.error(BetxEventCategory.ERROR, "config.validation.failed")
                    .result("failed")
                    .field("errorType", exception.getClass().getSimpleName())
                    .field("message", exception.getMessage())
                    .emit();
                commandLine.getErr().println(exception.getMessage());
                return 1;
            })
            .execute(args);
        eventLogger.info(BetxEventCategory.OPERATIONAL, "app.stopped")
            .result("stopped")
            .field("exitCode", exitCode)
            .emit();
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

    private String commandName(String[] args) {
        if (args == null) {
            return "unknown";
        }
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if ("--config".equals(arg) || "-c".equals(arg)) {
                index++;
                continue;
            }
            if (arg != null && !arg.startsWith("-")) {
                return arg;
            }
        }
        return "root";
    }
}
