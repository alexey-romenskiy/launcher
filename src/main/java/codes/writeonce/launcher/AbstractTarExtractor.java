package codes.writeonce.launcher;

import codes.writeonce.repository.Resource;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.HashSet;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;

public abstract class AbstractTarExtractor extends AbstractExtractor {

    @Override
    public void extract(@Nonnull Resource resource, @Nonnull Path destinationPath) throws IOException {

        final var rootPath = destinationPath.toRealPath();

        final var posix = Files.getFileStore(rootPath).supportsFileAttributeView(PosixFileAttributeView.class);

        final var createdDirs = new HashSet<Path>();
        createdDirs.add(rootPath);

        final var dirs = new TreeMap<Path, DirInfo>();
        final var symlinks = new TreeMap<Path, SymlinkInfo>();

        final var bytes = new byte[0x1000000];

        try (var resourceInputStream = resource.getInputStream();
             var realInputStream = getInputStream(resourceInputStream);
             var in = new TarArchiveInputStream(realInputStream, UTF_8.name())) {

            while (true) {
                final var entry = in.getNextTarEntry();
                if (entry == null) {
                    break;
                }

                final var entryPath = rootPath.resolve(entry.getName()).normalize();
                final var parent = entryPath.getParent();
                if (!parent.startsWith(rootPath)) {
                    throw new IllegalArgumentException();
                }

                final var lastModified = FileTime.from(entry.getLastModifiedDate().toInstant());
                final var permissions = fromMode(entry.getMode());

                if (entry.isFile()) {
                    final var size = entry.getSize();
                    if (createdDirs.add(parent)) {
                        Files.createDirectories(parent);
                    }
                    try (var out = Files.newOutputStream(entryPath)) {
                        var remained = size;
                        while (remained != 0) {
                            final var read = in.read(bytes, 0, (int) Math.min(bytes.length, remained));
                            if (read < 0) {
                                throw new IllegalArgumentException();
                            }
                            remained -= read;
                            out.write(bytes, 0, read);
                        }
                    }
                    if (posix) {
                        Files.setPosixFilePermissions(entryPath, permissions);
                    }
                    Files.setLastModifiedTime(entryPath, lastModified);
                } else if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                    dirs.put(entryPath, new DirInfo(lastModified, permissions));
                    createdDirs.add(parent);
                } else if (entry.isSymbolicLink()) {
                    symlinks.put(entryPath, new SymlinkInfo(Path.of(entry.getLinkName()), lastModified, permissions));
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }

        for (final var entry : dirs.descendingMap().entrySet()) {
            final var path = entry.getKey();
            final var info = entry.getValue();
            if (posix) {
                Files.setPosixFilePermissions(path, info.permissions);
            }
            Files.setLastModifiedTime(path, info.time);
        }

        for (final var entry : symlinks.descendingMap().entrySet()) {
            final var path = entry.getKey();
            final var info = entry.getValue();
            final var parent = path.getParent();
            if (createdDirs.add(parent)) {
                Files.createDirectories(parent);
            }
            Files.createSymbolicLink(path, info.path, asFileAttribute(info.permissions));
            Files.setLastModifiedTime(path, info.time);
        }
    }

    @Nonnull
    protected abstract InputStream getInputStream(@Nonnull InputStream inputStream) throws IOException;
}
