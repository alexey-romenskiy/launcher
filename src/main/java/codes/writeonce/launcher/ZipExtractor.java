package codes.writeonce.launcher;

import codes.writeonce.repository.Resource;
import org.apache.commons.compress.archivers.zip.ZipFile;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;

public class ZipExtractor extends AbstractExtractor {

    @Override
    public void extract(@Nonnull Resource resource, @Nonnull Path destinationPath)
            throws ExecutionException, InterruptedException, IOException {

        final var rootPath = destinationPath.toRealPath();

        final var posix = Files.getFileStore(rootPath).supportsFileAttributeView(PosixFileAttributeView.class);

        final var createdDirs = new HashSet<Path>();
        createdDirs.add(rootPath);

        final var dirs = new TreeMap<Path, DirInfo>();
        final var symlinks = new TreeMap<Path, SymlinkInfo>();

        final var bytes = new byte[0x1000000];

        try (var zipFile = new ZipFile(resource.getCompletableFuture().get().toFile(), UTF_8.name())) {

            final var entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                final var entry = entries.nextElement();

                final var entryPath = rootPath.resolve(entry.getName()).normalize();
                final var parent = entryPath.getParent();
                if (!parent.startsWith(rootPath)) {
                    throw new IllegalArgumentException();
                }

                final var lastModified = FileTime.from(entry.getLastModifiedDate().toInstant());
                final var permissions = fromMode(entry.getUnixMode());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                    dirs.put(entryPath, new DirInfo(lastModified, permissions));
                    createdDirs.add(parent);
                } else if (entry.isUnixSymlink()) {
                    symlinks.put(entryPath,
                            new SymlinkInfo(Path.of(zipFile.getUnixSymlink(entry)), lastModified, permissions));
                } else {
                    if (createdDirs.add(parent)) {
                        Files.createDirectories(parent);
                    }
                    try (var in = zipFile.getInputStream(entry);
                         var out = Files.newOutputStream(entryPath)) {
                        while (true) {
                            final var read = in.read(bytes, 0, bytes.length);
                            if (read == -1) {
                                break;
                            }
                            out.write(bytes, 0, read);
                        }
                    }
                    if (posix) {
                        Files.setPosixFilePermissions(entryPath, permissions);
                    }
                    Files.setLastModifiedTime(entryPath, lastModified);
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
}
