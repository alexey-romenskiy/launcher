package codes.writeonce.launcher;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class StartMain extends AbstractMain {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        new StartMain().run(args);
    }

    @Override
    protected void doStart(
            Path pidPath,
            Properties configProperties,
            String commandArguments,
            Properties environmentProperties,
            String javaCmd,
            Path commandPath,
            Path logPath,
            Path workPath
    ) throws IOException {

        final Process process;
        if (Files.exists(Path.of("/dev/fd"))) {
            process =
                    commonStart(configProperties, environmentProperties, javaCmd, logPath, workPath, "@/dev/fd/0");
            try (var out = process.getOutputStream();
                 var writer = new OutputStreamWriter(out)) {
                writer.write(commandArguments);
            }
        } else {
            final var argsPath = commandPath.resolve("args");
            try (var out = Files.newOutputStream(argsPath);
                 var writer = new OutputStreamWriter(out)) {
                writer.write(commandArguments);
            }
            process = commonStart(configProperties, environmentProperties, javaCmd, logPath, workPath,
                    '@' + argsPath.toString());
            process.getOutputStream().close();
        }

        Files.writeString(pidPath, String.valueOf(process.pid()), UTF_8);
    }

    @Nonnull
    private static Process commonStart(
            @Nonnull Properties configProperties,
            @Nonnull Properties environmentProperties,
            @Nonnull String javaCmd,
            @Nonnull Path logPath,
            @Nonnull Path workPath,
            @Nonnull String args
    ) throws IOException {

        final var time = configProperties.getProperty("time.fileName");
        final var pb = new ProcessBuilder(javaCmd, args);
        final var env = pb.environment();
        for (final var name : environmentProperties.stringPropertyNames()) {
            env.put(name, environmentProperties.getProperty(name));
        }
        pb.redirectOutput(Redirect.appendTo(logPath.resolve("output." + time + ".log").toFile()));
        pb.redirectErrorStream(true);
        pb.directory(workPath.toFile());
        return pb.start();
    }
}
