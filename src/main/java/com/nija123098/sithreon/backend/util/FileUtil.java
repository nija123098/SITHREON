package com.nija123098.sithreon.backend.util;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.util.throwable.IOExceptionWrapper;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FileUtil {
    /**
     * Consumes an operation on a {@link File}, while maintain a lock on it.
     *
     * @param file the {@link File} to manage while locked.
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
     * @param file the {@link File} to the {@link KeyStore}.
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
     * @param file the {@link File} to write to.
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
     * @param file the {@link File} to read and write from.
     * @param password the password to read and write the keystore with.
     * @param storeConsumer the {@link Consumer<KeyStore>} to modify the {@link KeyStore}.
     */
    public static void modifyKeyStore(File file, char[] password, Consumer<KeyStore> storeConsumer) {
        KeyStore keyStore = getLoadedKeyStore(file, password);
        storeConsumer.accept(keyStore);
        writeKeyStore(file, keyStore, password);
    }

    /**
     * Walks the file tree of the specified {@link Path} with the option to filter out parts of the tree.
     *
     * @param root the root of the file tree walk.
     * @param pathPredicate the predicate for determining if
     * @param pathConsumer the consumer for files and folders accepted by the predicate.
     * @param options the {@link FileVisitOption}s for walking the file tree.
     * @throws IOException if an I/O error is thrown when accessing a file.
     */
    public static void walk(Path root, Predicate<Path> pathPredicate, Consumer<Path> pathConsumer, FileVisitOption... options) throws IOException {
        if (!pathPredicate.test(root)) return;
        if (Files.isDirectory(root)) {
            try {
                Files.walk(root, 1, options).forEach(path -> {
                    try {
                        walk(path, pathPredicate, pathConsumer);
                    } catch (IOException e) {
                        throw new IOExceptionWrapper(e);
                    }
                });
            } catch (IOExceptionWrapper e) {
                throw e.getCause();
            }
        }
        pathConsumer.accept(root);
    }
}
