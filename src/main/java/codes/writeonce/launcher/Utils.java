package codes.writeonce.launcher;

import codes.writeonce.repository.Repository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class Utils {

    @Nonnull
    public static Properties getConfigProperties(@Nonnull Path configPath) throws IOException {
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalArgumentException();
        }
        final var configProperties = new Properties();
        try (var in = Files.newInputStream(configPath)) {
            configProperties.load(in);
        }
        return configProperties;
    }

    @Nonnull
    public static Path getTmpPath(@Nonnull Path path) {
        return path.getParent().resolve(path.getFileName().toString() + ".tmp");
    }

    @Nonnull
    public static Path getCommandPath(@Nonnull Repository repository, @Nonnull String profile,
            @Nonnull String command) {
        return repository.launcherDir.resolve("commands").resolve(profile).resolve(command);
    }

    @Nonnull
    public static Path getPidPath(@Nonnull Repository repository, @Nonnull String profile,
            @Nonnull String command) {
        return getRunPath(repository, profile, command).resolve("process.pid");
    }

    @Nonnull
    public static Path getRunPath(@Nonnull Repository repository, @Nonnull String profile,
            @Nonnull String command) {
        return getCommandPath(repository, profile, command).resolve("run");
    }

    @Nullable
    public static Long getPid(@Nonnull Path pidPath) throws IOException {
        final String pid;
        try {
            pid = Files.readString(pidPath, UTF_8);
        } catch (NoSuchFileException ignore) {
            return null;
        }
        return Long.parseLong(pid);
    }

    private Utils() {
        // empty
    }
}
