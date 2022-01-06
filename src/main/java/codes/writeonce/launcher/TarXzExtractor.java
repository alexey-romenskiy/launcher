package codes.writeonce.launcher;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

public class TarXzExtractor extends AbstractTarExtractor {

    @Nonnull
    @Override
    protected InputStream getInputStream(@Nonnull InputStream inputStream) throws IOException {
        return new XZCompressorInputStream(inputStream, true);
    }
}
