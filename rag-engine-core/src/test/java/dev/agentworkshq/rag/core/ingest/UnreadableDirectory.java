package dev.agentworkshq.rag.core.ingest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class UnreadableDirectory implements AutoCloseable {

    private final Restore restore;

    private UnreadableDirectory(Restore restore) {
        this.restore = restore;
    }

    static UnreadableDirectory make(Path directory) throws IOException {
        UnreadableDirectory result = new UnreadableDirectory(lock(directory));
        // some users, root for example, can list a directory whatever its permissions say
        if (canList(directory)) {
            result.close();
            assumeTrue(false, "cannot make an unreadable directory here");
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        restore.run();
    }

    private static Restore lock(Path directory) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> before = posix.readAttributes().permissions();
            posix.setPermissions(Set.of());
            return () -> posix.setPermissions(before);
        }
        AclFileAttributeView acl = Files.getFileAttributeView(directory, AclFileAttributeView.class);
        assumeTrue(acl != null, "no permission model to work with");
        List<AclEntry> before = acl.getAcl();
        UserPrincipal user = directory.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        List<AclEntry> denied = new ArrayList<>(before);
        denied.add(0, AclEntry.newBuilder().setType(AclEntryType.DENY).setPrincipal(user)
                .setPermissions(AclEntryPermission.LIST_DIRECTORY).build());
        acl.setAcl(denied);
        return () -> acl.setAcl(before);
    }

    private static boolean canList(Path directory) {
        try (DirectoryStream<Path> ignored = Files.newDirectoryStream(directory)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private interface Restore {
        void run() throws IOException;
    }
}
