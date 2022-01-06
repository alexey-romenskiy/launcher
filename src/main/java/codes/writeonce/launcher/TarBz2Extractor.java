package codes.writeonce.launcher;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

public class TarBz2Extractor extends AbstractTarExtractor {

    @Nonnull
    @Override
    protected InputStream getInputStream(@Nonnull InputStream inputStream) throws IOException {
        return new BZip2CompressorInputStream(inputStream, true);
    }
}
