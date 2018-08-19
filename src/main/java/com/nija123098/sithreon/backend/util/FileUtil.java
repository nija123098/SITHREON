package com.nija123098.sithreon.backend.util;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.function.Consumer;

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
}
