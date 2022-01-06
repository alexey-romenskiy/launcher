package codes.writeonce.launcher;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

public class TarGzExtractor extends AbstractTarExtractor {

    @Nonnull
    @Override
    protected InputStream getInputStream(@Nonnull InputStream inputStream) throws IOException {
        return new GzipCompressorInputStream(inputStream, true);
    }
}
