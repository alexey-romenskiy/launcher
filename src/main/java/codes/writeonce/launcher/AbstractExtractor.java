package codes.writeonce.launcher;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

public abstract class AbstractExtractor implements Extractor {

    private static final PosixFilePermission[] PERMISSIONS = {
            OTHERS_EXECUTE,
            OTHERS_WRITE,
            OTHERS_READ,
            GROUP_EXECUTE,
            GROUP_WRITE,
            GROUP_READ,
            OWNER_EXECUTE,
            OWNER_WRITE,
            OWNER_READ
    };

    protected static class DirInfo {

        @Nonnull
        public final FileTime time;

        @Nonnull
        public final Set<PosixFilePermission> permissions;

        public DirInfo(@Nonnull FileTime time, @Nonnull Set<PosixFilePermission> permissions) {
            this.time = time;
            this.permissions = permissions;
        }
    }

    protected static class SymlinkInfo {

        @Nonnull
        public final Path path;

        @Nonnull
        public final FileTime time;

        @Nonnull
        public final Set<PosixFilePermission> permissions;

        public SymlinkInfo(@Nonnull Path path, @Nonnull FileTime time, @Nonnull Set<PosixFilePermission> permissions) {
            this.path = path;
            this.time = time;
            this.permissions = permissions;
        }
    }

    @Nonnull
    protected static Set<PosixFilePermission> fromMode(int mode) {

        final var permissions = new HashSet<PosixFilePermission>(PERMISSIONS.length);
        for (int i = 0; i < PERMISSIONS.length; i++) {
            if ((mode & (1 << i)) != 0) {
                permissions.add(PERMISSIONS[i]);
            }
        }
        return permissions;
    }
}
