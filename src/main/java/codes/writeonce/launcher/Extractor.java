package codes.writeonce.launcher;

import codes.writeonce.repository.Resource;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public interface Extractor {

    void extract(@Nonnull Resource resource, @Nonnull Path destinationPath)
            throws IOException, ExecutionException, InterruptedException;
}
