package codes.writeonce.launcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class DryRunMain extends AbstractMain {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        new DryRunMain().run(args);
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
    ) {
        // empty
    }
}
