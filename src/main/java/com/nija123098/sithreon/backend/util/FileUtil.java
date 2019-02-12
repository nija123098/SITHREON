package com.nija123098.sithreon.backend.util;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.objects.BuildType;
import com.nija123098.sithreon.backend.util.throwable.IOExceptionWrapper;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FileUtil {
    /**
     * Consumes an operation on a {@link File}, while maintain a lock on it.
     *
     * @param file         the {@link File} to manage while locked.
     * @param fileConsumer the operation on the locked file.
     */
    private static void manageLockFile(File file, Consumer<File> fileConsumer) {
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new SithreonException("Unable to make file for locking", e);
        }
        FileChannel fileChannel;
        try {
            fileChannel = new RandomAccessFile(Config.trustedCertificateStorage, "rw").getChannel();
        } catch (FileNotFoundException e) {
            Log.ERROR.log("Unexpected FileNotFound exception", e);
            throw new NoReturnException();
        }
        FileLock fileLock = null;
        while (fileLock == null) {
            try {
                fileLock = fileChannel.lock();
            } catch (IOException e) {
                Log.ERROR.log("IOException during initialization of " + file + " lock", e);
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Log.ERROR.log("Interrupted during initialization of " + file + " lock", e);
            }
        }
        fileConsumer.accept(Config.trustedCertificateStorage);
        try {
            fileLock.release();
        } catch (IOException e) {
            Log.ERROR.log("IOException releasing lock for " + file + ", have fun unlocking", e);
        }
    }

    /**
     * Loads a {@link KeyStore} from a {@link File} and password.
     *
     * @param file     the {@link File} to the {@link KeyStore}.
     * @param password the password to read the {@link KeyStore}.
     * @return the {@link KeyStore} instance.
     */
    public static KeyStore getLoadedKeyStore(File file, char[] password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (file == null) keyStore.load(null, password);
            else keyStore.load(new FileInputStream(file), password);
            return keyStore;
        } catch (FileNotFoundException e) {
            return getLoadedKeyStore(null, password);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            if (file == null) Log.ERROR.log("Unable to load empty KeyStore");
            else Log.ERROR.log("Unable to load keystore " + file.getAbsolutePath(), e);
            throw new NoReturnException();
        }
    }

    /**
     * Writes the {@link KeyStore} instance to a {@link File}.
     *
     * @param file     the {@link File} to write to.
     * @param keyStore the {@link KeyStore} to write data from.
     * @param password the password to encrypt the keystore with.
     */
    public static void writeKeyStore(File file, KeyStore keyStore, char[] password) {
        try {
            file.createNewFile();
            keyStore.store(new FileOutputStream(file, false), password);
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads then writes the modified {@link KeyStore} to the specified {@link File}.
     *
     * @param file          the {@link File} to read and write from.
     * @param password      the password to read and write the keystore with.
     * @param storeConsumer the {@link Consumer<KeyStore>} to modify the {@link KeyStore}.
     */
    public static void modifyKeyStore(File file, char[] password, Consumer<KeyStore> storeConsumer) {
        KeyStore keyStore = getLoadedKeyStore(file, password);
        storeConsumer.accept(keyStore);
        writeKeyStore(file, keyStore, password);
    }

    /**
     * Walks the file tree of the specified {@link File} with the option to filter out parts of the tree.
     *
     * @param root          the root of the file tree walk.
     * @param pathPredicate the predicate for determining if
     * @param pathConsumer  the consumer for files and folders accepted by the predicate.
     * @throws IOException if an I/O error is thrown when accessing a file.
     */
    public static void walk(File root, Predicate<File> pathPredicate, Consumer<File> pathConsumer) throws IOException {
        if (!pathPredicate.test(root)) return;
        if (root.isDirectory()) {
            try {
                File[] files = root.listFiles();
                if (files == null) {
                    throw new IOException("Null received for list of files in directory: " + root.getAbsolutePath());
                }
                for (File file : files) {
                    walk(file, pathPredicate, pathConsumer);
                }
            } catch (IOExceptionWrapper e) {
                throw e.getCause();
            }
        }
        pathConsumer.accept(root);
    }

    /**
     * Deletes all files under the path.
     *
     * @param path the path to start deleting from.
     * @throws IOException if an I/O error occurs.
     */
    public static void deleteFiles(Path path) throws IOException {
        try {
            Files.walk(path, FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).forEach(target -> {
                try {
                    Files.delete(target);
                } catch (IOException e) {
                    throw new IOExceptionWrapper(e);
                }
            });
        } catch (IOExceptionWrapper e) {
            throw e.getCause();
        }
    }

    /**
     * A file location of the sithreon.jar file running this instance, which may be a URL.
     * <p>
     * Use is discouraged in favor of configurable locations unless the JAR file it's self is needed.
     *
     * @return a file location of this instances JAR.
     */
    public static String getSithreonLocation() {
        try {
            return new File(BuildType.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath() + File.separator;
        } catch (URISyntaxException e) {
            Log.ERROR.log("Unable to get file path", e);
            throw new NoReturnException();
        }
    }

    /**
     * If the path points to a local path or directory.
     *
     * @param s the path to check.
     * @return if the path is local.
     */
    public static boolean isLocalPath(String s) {
        return s.startsWith("/") || (Character.isAlphabetic(s.charAt(0)) && Character.isUpperCase(s.charAt(0)) && s.charAt(1) == ':' && s.charAt(2) == '\\') || !s.contains(":") || (s.indexOf(":") > (s.indexOf('/') == -1 ? s.indexOf('\\') : s.indexOf("/")));
    }

    /**
     * Returns a plausible temporary directory.
     * <p>
     * {@link Config#tmpDirectory}
     *
     * @return the temporary directory to use.
     */
    public static String getTemporaryDirectory() {
        return (Files.exists(Paths.get("/dev/shm/")) ? "/dev/shm/sithreon/" : "tmp/sithreon/").replace("/", File.separator);
    }
}
