package com.nija123098.sithreon.machine;

import com.nija123098.sithreon.Config;
import com.nija123098.sithreon.machine.transfers.Transfer;
import com.nija123098.sithreon.util.Log;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

/**
 * The wrapper object for the end to end encrypted {@link Transfer}s
 * for ease of transferring objects between {@link TransferSocket}s.
 */
class WrapperObject implements Serializable {
    private static final Cipher ENCRYPTION_CIPHER;
    private static final Cipher DECRYPTION_CIPHER;

    static {
        try {
            ENCRYPTION_CIPHER = Cipher.getInstance(Config.encryptionAlgorithm);
            DECRYPTION_CIPHER = Cipher.getInstance(Config.encryptionAlgorithm);
            Key key = new SecretKeySpec(Config.encryptionKey.getBytes("UTF-8"), Config.encryptionAlgorithm);
            ENCRYPTION_CIPHER.init(Cipher.ENCRYPT_MODE, key);
            DECRYPTION_CIPHER.init(Cipher.DECRYPT_MODE, key);
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException ex) {
            Log.ERROR.log("Invalid security settings", ex);
            throw new RuntimeException();// Won't occur
        } catch (UnsupportedEncodingException e) {
            Log.ERROR.log("UTF-8 is apparently no longer a thing");
            throw new RuntimeException();// Won't occur
        }
    }

    /**
     * The encrypted bytes of the {@link Transfer}.
     */
    private byte[] bytes;

    /**
     * Constructs a wrapper for the encrypted {@link Transfer}.
     *
     * @param transfer the {@link Transfer} to wrap.
     * @throws IOException if something that won't go wrong.
     */
    WrapperObject(Transfer transfer) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteStream);
        outputStream.writeObject(transfer);
        try {
            this.bytes = ENCRYPTION_CIPHER.doFinal(byteStream.toByteArray());
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            Log.ERROR.log("Invalid security settings", ex);
        }
    }

    /**
     * Decrypts the wrapped {@link Transfer}.
     *
     * @return the decrypted wrapped {@link Transfer}.
     * @throws IOException            if the underlying streams throw an {@link IOException}.
     * @throws ClassNotFoundException if there is no exact class equivalent to the transfer.
     */
    Transfer getObject() throws IOException, ClassNotFoundException {
        try {
            return (Transfer) new ObjectInputStream(new ByteArrayInputStream(DECRYPTION_CIPHER.doFinal(this.bytes))).readObject();
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.ERROR.log("Invalid security settings", e);
            return null;// won't return
        }
    }
}
