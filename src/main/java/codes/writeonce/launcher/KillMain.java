package codes.writeonce.launcher;

import codes.writeonce.repository.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

import static codes.writeonce.launcher.Utils.getPid;
import static codes.writeonce.launcher.Utils.getPidPath;

public class KillMain {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        final var profile = args[0];
        final var command = args[1];

        try (var repository = new Repository()) {

            final var pidPath = getPidPath(repository, profile, command);
            final var pid = getPid(pidPath);
            if (pid == null) {
                System.out.println("Process PID file not found");
            } else {
                final var processHandle = ProcessHandle.of(pid).orElse(null);
                if (processHandle == null) {
                    System.out.println("Process not found for PID=" + pid);
                    Files.deleteIfExists(pidPath);
                } else if (!processHandle.isAlive()) {
                    System.out.println("Process is not alive for PID=" + pid);
                    Files.deleteIfExists(pidPath);
                } else if (processHandle.destroyForcibly()) {
                    System.out.println("Process forced terination initiated for PID=" + pid);
                    processHandle.onExit().get();
                    System.out.println("Process terminated forcibly for PID=" + pid);
                    Files.deleteIfExists(pidPath);
                } else {
                    System.out.println("Cannot terminate process for PID=" + pid);
                }
            }
        }
    }
}
