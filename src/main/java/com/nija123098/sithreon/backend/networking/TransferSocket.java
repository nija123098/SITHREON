package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.machines.SuperServer;
import com.nija123098.sithreon.backend.util.ByteBuffer;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.ThreadMaker;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper for a {@link Socket} which manages sending
 * end to end encrypted {@link MachineAction} commands.
 *
 * @author nija123098
 */
public class TransferSocket {
    private static final Random RANDOM = new Random();
    private static final Cipher ENCRYPTION_CIPHER;
    private static final Cipher DECRYPTION_CIPHER;
    /**
     * The hashing algorithm represented as a {@link MessageDigest}.
     */
    private static final MessageDigest DIGEST;

    static {
        try {
            ENCRYPTION_CIPHER = Cipher.getInstance(Config.encryptionAlgorithm);
            DECRYPTION_CIPHER = Cipher.getInstance(Config.encryptionAlgorithm);
            Key key = new SecretKeySpec(Config.encryptionKey.getBytes(Charset.forName("UTF-8")), Config.encryptionAlgorithm);
            ENCRYPTION_CIPHER.init(Cipher.ENCRYPT_MODE, key);
            DECRYPTION_CIPHER.init(Cipher.DECRYPT_MODE, key);
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException ex) {
            Log.ERROR.log("Invalid security settings", ex);
            throw new RuntimeException();// Won't occur
        }
    }

    static {
        try {
            DIGEST = MessageDigest.getInstance(Config.hashingAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            Log.ERROR.log("Invalid security settings", e);
            throw new RuntimeException();// Won't occur
        }
    }

    private final byte[] hashRequest = new byte[32];
    private final long authenticationTime = System.currentTimeMillis();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final Machine localMachine;
    private final AtomicBoolean authenticated = new AtomicBoolean();
    private final AtomicInteger nonce = new AtomicInteger(RANDOM.nextInt());

    /**
     * Constructs a client socket instance for connecting to a server.
     *
     * @param localMachine the machine this instance belongs to.
     * @param host         the host address of the {@link Machine} to connect to.
     * @throws IOException if the {@link Socket} constructor throws an {@link IOException}.
     */
    public TransferSocket(Machine localMachine, String host) throws IOException {
        this(localMachine, new Socket(host, Config.port));
    }

    /**
     * Constructs a wrapper for a socket that has been accepted by a server.
     *
     * @param localMachine the {@link Machine} this instance belongs to.
     * @param socket       the accepted socket from a {@link SocketAcceptor}.
     * @throws IOException if flushing or a stream throws an {@link IOException}.
     */
    TransferSocket(Machine localMachine, Socket socket) throws IOException {
        this.localMachine = localMachine;
        this.socket = socket;
        this.outputStream = this.socket.getOutputStream();
        this.outputStream.flush();
        this.inputStream = this.socket.getInputStream();
        ThreadMaker.getThread(ThreadMaker.NETWORK, "TransferSocketReadThread" + this.socket.getRemoteSocketAddress(), true, () -> {
            RANDOM.nextBytes(this.hashRequest);
            this.write(MachineAction.REQUEST_AUTHENTICATION, this.hashRequest, Config.machineId.getBytes(Charset.forName("UTF-8")), this.authenticationTime);
            boolean decryptMore = false;
            byte[] buffer = new byte[1024];
            byte[] decrypted;
            int readSize, encryptedSize = -1;
            ByteBuffer pendingBytes = new ByteBuffer(), unEncryptedBytes = new ByteBuffer();
            Object[] machineActionArgs;
            while (!this.closed.get()) {
                try {
                    if (!decryptMore) {
                        readSize = this.inputStream.read(buffer);
                        if (this.closed.get()) return;
                        if (readSize == -1) {
                            Log.INFO.log("Opposite side closed " + this.socket.getRemoteSocketAddress());
                            this.close();
                        }
                        for (int i = 0; i < readSize; i++) unEncryptedBytes.add(buffer[i]);
                    }
                    decryptMore = false;
                    if (unEncryptedBytes.size() > Integer.BYTES) {
                        if (encryptedSize == -1)
                            encryptedSize = ObjectSerialization.deserialize(Integer.class, unEncryptedBytes.getBytes(true, Integer.BYTES));
                        if (unEncryptedBytes.size() >= encryptedSize) {
                            decryptMore = true;
                            try {
                                decrypted = DECRYPTION_CIPHER.doFinal(unEncryptedBytes.getBytes(true, encryptedSize));
                                pendingBytes.add(Arrays.copyOfRange(decrypted, Long.BYTES + 1, decrypted.length));
                                encryptedSize = -1;
                            } catch (IllegalBlockSizeException | BadPaddingException e) {
                                if (this.authenticated.get()) Log.ERROR.log("Invalid security settings", e);
                                else {
                                    Log.WARN.log("Connection from " + this.socket.getRemoteSocketAddress() + " failed authentication");
                                    this.close();
                                }
                            }
                            MachineAction machineAction = MachineAction.values()[pendingBytes.get(0)];
                            machineActionArgs = machineAction.read(pendingBytes);
                            if (machineActionArgs == null) continue;
                            if ((machineAction != MachineAction.REQUEST_AUTHENTICATION && machineAction != MachineAction.AUTHENTICATE) && !this.authenticated.get())
                                Log.WARN.log("Connection from " + this.socket.getRemoteSocketAddress() + " attempted using non-authentication MachineCation before being authenticated");
                            else machineAction.act(this, machineActionArgs);
                        }
                    }
                } catch (EOFException e) {
                    Log.INFO.log("Opposite side closed " + this.socket.getRemoteSocketAddress());
                    this.close();
                } catch (SocketException e) {
                    switch (e.getMessage()) {
                        case "Connection reset":
                            Log.INFO.log("Opposite side closed " + this.socket.getRemoteSocketAddress());
                            this.close();
                            break;
                        case "Socket closed":
                            if (!this.closed.get()) {
                                Log.WARN.log("Opposite side unexpectedly closed connection", e);
                                this.close();
                            }
                            break;
                        default:
                            Log.WARN.log("SocketException receiving object", e);
                            break;
                    }
                } catch (IOException e) {
                    Log.WARN.log("IOException receiving object", e);
                }
            }
        }).start();
        this.localMachine.runOnClose(this::close);
    }

    /**
     * Gets the {@link Machine} this instance belongs to.
     *
     * @return the {@link Machine} this instance belongs to.
     */
    public Machine getLocalMachine() {
        return this.localMachine;
    }

    /**
     * Writes a {@link MachineAction} command to the stream.
     *
     * @param action the action to preform.
     * @param args   the arguments for the action.
     */
    public void write(MachineAction action, Object... args) {
        try {
            this.write(action.write(args));
            Log.TRACE.log("Flushed MachineAction of type " + action);
        } catch (IOException e) {
            Log.WARN.log("IOException flushing MachineAction of type " + action, e);
        }
    }

    /**
     * Writes the bytes provided to the stream after encrypting them.
     *
     * @param bytes the bytes to write to the stream.
     * @throws IOException if there are problems writing to the stream.
     */
    private synchronized void write(byte... bytes) throws IOException {
        try {
            byte[] toEncrypt = new byte[bytes.length + Long.BYTES + 1];
            toEncrypt[0] = (byte) this.nonce.incrementAndGet();
            System.arraycopy(ObjectSerialization.serialize(Long.class, System.currentTimeMillis()), 0, toEncrypt, 1, Long.BYTES);
            System.arraycopy(bytes, 0, toEncrypt, Long.BYTES + 1, bytes.length);
            byte[] encrypted = ENCRYPTION_CIPHER.doFinal(toEncrypt);
            this.outputStream.write(ObjectSerialization.serialize(Integer.class, encrypted.length));
            this.outputStream.write(encrypted);
            this.outputStream.flush();
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.ERROR.log("Invalid security settings", e);
        }
    }

    /**
     * Closes the wrapped socket.
     */
    public void close() {
        if (this.closed.get()) return;
        this.getLocalMachine().deregisterSocket(this);
        this.closed.set(true);
        try {
            this.inputStream.close();
            this.outputStream.close();
            this.socket.close();
        } catch (IOException e) {
            Log.ERROR.log("IOException closing socket to " + this.socket.getRemoteSocketAddress(), e);
        }
        Log.INFO.log("Closed socket to " + this.socket.getRemoteSocketAddress());
        if (!(this.getLocalMachine() instanceof SuperServer)) {
            Log.INFO.log("Closing machine due to closing connection to the server");
            this.getLocalMachine().close();
        }
    }

    /**
     * @param hashRequest        the random byte sequence to hash with.
     * @param machineId          the machine id to hash with.
     * @param authenticationTime the time the authentication was sent to hash with.
     * @see MachineAction#REQUEST_AUTHENTICATION
     */
    @Action(MachineAction.REQUEST_AUTHENTICATION)
    public void requestAuthentication(byte[] hashRequest, byte[] machineId, Long authenticationTime) {
        this.write(MachineAction.AUTHENTICATE, this.getHash(hashRequest, machineId, authenticationTime), true);
    }

    /**
     * Authenticates the opposite machine.
     *
     * @param hash         the hash the other machine has computed.
     * @param authenticate if the machine wants to authenticate.
     * @see MachineAction#AUTHENTICATE
     */
    @Action(MachineAction.AUTHENTICATE)
    public void authenticate(byte[] hash, Boolean authenticate) {// authenticate here is for not confusing var args
        if (Arrays.equals(this.getHash(this.hashRequest, Config.machineId.getBytes(Charset.forName("UTF-8")), this.authenticationTime), hash) && authenticate && this.authenticationTime <= System.currentTimeMillis() && this.authenticationTime > System.currentTimeMillis() - 15_000) {
            this.authenticated.set(true);
            Log.INFO.log("Connection from " + this.socket.getRemoteSocketAddress() + " authenticated");
            this.localMachine.registerSocket(this);
        } else {
            Log.WARN.log("Connection from " + this.socket.getRemoteSocketAddress() + " failed authentication");
            this.close();
        }
    }

    private byte[] getHash(byte[] hashRequest, byte[] machineId, long time) {// with integrated time
        byte[] digestion = new byte[Config.authenticationKey.length() + hashRequest.length + machineId.length + Long.BYTES];
        byte[] keyBytes = Config.authenticationKey.getBytes(Charset.forName("UTF-8"));
        System.arraycopy(hashRequest, 0, digestion, 0, hashRequest.length);
        System.arraycopy(keyBytes, 0, digestion, hashRequest.length, keyBytes.length);
        System.arraycopy(machineId, 0, digestion, hashRequest.length + keyBytes.length, machineId.length);
        System.arraycopy(ObjectSerialization.serialize(Long.class, time), 0, digestion, hashRequest.length + keyBytes.length + machineId.length, Long.BYTES);
        return DIGEST.digest(digestion);// hashes the digestion
    }
}
