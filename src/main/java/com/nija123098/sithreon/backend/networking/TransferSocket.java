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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A wrapper for a {@link Socket} which manages sending
 * end to end encrypted {@link MachineAction} commands.
 *
 * @author nija123098
 */
public class TransferSocket implements Comparable<TransferSocket> {
    /**
     * The amount of time the socket has to authenticate.
     */
    private static final long AUTHENTICATION_TIME = 15_000;
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
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
            Log.ERROR.log("Invalid security settings", e);
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

    /**
     * The random bytes to produce the authentication hash with.
     */
    private final byte[] hashRequest = new byte[32];

    /**
     * The time to compare for authentication for time out due to no authentication.
     */
    private final long authenticationTime = System.currentTimeMillis();

    /**
     * If this instance is closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * The {@link Socket} this wraps.
     */
    private final Socket socket;

    /**
     * The {@link InputStream} for the {@code socket}.
     */
    private final InputStream inputStream;

    /**
     * The {@link OutputStream} for the {@code socket}.
     */
    private final OutputStream outputStream;

    /**
     * The {@link Machine} that this instance belongs to.
     */
    private final Machine localMachine;

    /**
     * If the connection is authentic.
     */
    private final AtomicBoolean authenticated = new AtomicBoolean();

    /**
     * If the other side knows that the connection is authentic.
     */
    private final AtomicBoolean otherAuthenticated = new AtomicBoolean();

    /**
     * The nonce that increments each time data is encrypted.
     * Reduced to a single byte and combined with the time when being used.
     */
    private final AtomicInteger nonce = new AtomicInteger(RANDOM.nextInt());

    /**
     * The name of the other machine.
     */
    private final AtomicReference<String> machineName = new AtomicReference<>("No Connection");

    /**
     * The priority of the other machine for use in {@link TransferSocket#compareTo(Object)}.
     */
    private final AtomicInteger machinePriority = new AtomicInteger();

    /**
     * The {@link Consumer}s to run after authenticity has been established.
     */
    private final List<Consumer<TransferSocket>> postAuth = new ArrayList<>();

    /**
     * The {@link Runnable} to run on close of this instance.
     */
    private final AtomicReference<Runnable> onCloseReference = new AtomicReference<>();

    /**
     * Constructs a client socket instance for connecting to a server.
     *
     * @param localMachine the machine this instance belongs to.
     * @param host         the host address of the {@link Machine} to connect to.
     * @throws IOException if the {@link Socket} constructor throws an {@link IOException}.
     */
    public TransferSocket(Machine localMachine, String host, Integer port) throws IOException {
        this(localMachine, new Socket(host, port));
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
        ThreadMaker.getThread(ThreadMaker.NETWORK, "Transfer Socket Read Thread" + this.getConnectionName(), true, () -> {// constantly reads
            RANDOM.nextBytes(this.hashRequest);
            this.write(MachineAction.REQUEST_AUTHENTICATION, this.hashRequest, Config.machineId, Config.priority, this.authenticationTime);
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
                            Log.INFO.log("Opposite side closed " + this.getConnectionName());
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
                                decrypted = unEncryptedBytes.getBytes(true, encryptedSize);
                                for (int i = 0; i < Config.encryptionRounds; i++) {
                                    decrypted = DECRYPTION_CIPHER.doFinal(decrypted);
                                }
                                pendingBytes.add(Arrays.copyOfRange(decrypted, Long.BYTES + 1, decrypted.length));// checking for requests with the same nonce is unnecessary
                                encryptedSize = -1;// since at the point that it matters the connection must already be authentic
                            } catch (IllegalBlockSizeException | BadPaddingException e) {
                                if (this.authenticated.get()) Log.ERROR.log("Invalid security settings", e);
                                else {
                                    Log.WARN.log("Connection from " + this.getConnectionName() + " failed authentication");
                                    this.close();
                                }
                            }
                            MachineAction machineAction = MachineAction.values()[pendingBytes.get(0)];// the first byte determines the action
                            machineActionArgs = machineAction.read(this, pendingBytes);
                            if (machineActionArgs == null) continue;// args incomplete, wait for more data
                            if ((machineAction != MachineAction.REQUEST_AUTHENTICATION && machineAction != MachineAction.AUTHENTICATE) && !this.authenticated.get())
                                Log.WARN.log("Connection from " + this.getConnectionName() + " attempted using non-authentication MachineAction " + machineAction + " before being authenticated, dropping.");
                            else {
                                Log.TRACE.log("Received MachineAction of type " + machineAction + " from " + this.getConnectionName());
                                machineAction.act(this, machineActionArgs);
                            }
                        }
                    }
                } catch (EOFException e) {
                    Log.INFO.log("Opposite side closed " + this.getConnectionName());
                    this.close();
                } catch (SocketException e) {
                    switch (e.getMessage()) {
                        case "Connection reset":
                            Log.INFO.log("Opposite side closed " + this.getConnectionName());
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
        if (this.closed.get()) {
            if (action != MachineAction.CLOSE_ALL)
                Log.WARN.log("MachineAction dropped of type " + action + " to " + this.getConnectionName() + " due to close");// drop if everything is closing
            return;
        }
        try {
            this.write(action.write(args));
            Log.TRACE.log("Flushed MachineAction of type " + action + " to " + this.getConnectionName());
        } catch (IOException e) {
            if (action != MachineAction.CLOSE_ALL)
                Log.WARN.log("IOException flushing MachineAction of type " + action + " to " + this.getConnectionName(), e);
        }// drop if everything is closing
    }

    /**
     * Writes the bytes provided to the stream after encrypting them.
     *
     * @param bytes the bytes to write to the stream.
     * @throws IOException if there are problems writing to the stream.
     */
    private void write(byte... bytes) throws IOException {
        try {
            byte[] toEncrypt = new byte[1 + Long.BYTES + bytes.length];
            toEncrypt[0] = (byte) this.nonce.incrementAndGet();
            System.arraycopy(ObjectSerialization.serialize(Long.class, System.currentTimeMillis()), 0, toEncrypt, 1, Long.BYTES);
            System.arraycopy(bytes, 0, toEncrypt, Long.BYTES + 1, bytes.length);
            byte[] encrypted = toEncrypt;
            for (int i = 0; i < Config.encryptionRounds; i++) encrypted = ENCRYPTION_CIPHER.doFinal(encrypted);
            synchronized (this) {
                this.outputStream.write(ObjectSerialization.serialize(Integer.class, encrypted.length));
                this.outputStream.write(encrypted);
                this.outputStream.flush();
            }
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.ERROR.log("Invalid security settings", e);
        }
    }

    /**
     * Returns an appropriate name based on address
     * and the machine the connection identifies itself as.
     * <p>
     * This is intended as a human readable representation of the machine
     * connected, so it's exact contents are unspecified.
     *
     * @return a human readable representation for identifying the connected machine.
     */
    public String getConnectionName() {
        return this.socket.getRemoteSocketAddress().toString();
    }

    /**
     * Runs the provided {@link Runnable} to run on the close of this socket.
     *
     * @param runnable the {@link Runnable} to run on the close of this socket.
     */
    public void setOnClose(Runnable runnable) {
        this.onCloseReference.set(runnable);
    }

    /**
     * Closes the wrapped socket.
     */
    public void close() {
        if (this.closed.get()) return;
        this.getLocalMachine().deregisterSocket(this);
        this.closed.set(true);
        if (this.onCloseReference.get() != null) this.onCloseReference.get().run();
        try {
            this.inputStream.close();
            this.outputStream.close();
            this.socket.close();
        } catch (IOException e) {
            Log.ERROR.log("IOException closing socket to " + this.getConnectionName(), e);
        }
        Log.INFO.log("Closed socket to " + this.getConnectionName());
        if (!(this.getLocalMachine() instanceof SuperServer)) {
            Log.INFO.log("Closing machine due to closing connection to the server");
            this.getLocalMachine().close();
        }
    }

    /**
     * Handles requesting reasonable proof this machine knows the common {@link Config#authenticationKey}
     * so both a server and client can be sure that both are authentic agents of the network.
     *
     * @param hashRequest        the random byte sequence to hash with.
     * @param machineId          the other machine id to hash with.
     * @param priority           the other machine priority to hash with.
     * @param authenticationTime the time the authentication was sent to hash with.
     * @see MachineAction#REQUEST_AUTHENTICATION
     */
    @Action(MachineAction.REQUEST_AUTHENTICATION)
    public void requestAuthentication(byte[] hashRequest, String machineId, Integer priority, Long authenticationTime) {
        this.machinePriority.set(priority);
        this.machineName.set(machineId);
        this.write(MachineAction.AUTHENTICATE, getHash(hashRequest, machineId, authenticationTime), true);
        this.otherAuthenticated.set(true);
        if (this.authenticated.get()) {
            this.postAuth.forEach(consumer -> consumer.accept(this));
            this.postAuth.clear();
        }
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
        if (Arrays.equals(getHash(this.hashRequest, Config.machineId, this.authenticationTime), hash) && authenticate && this.authenticationTime <= System.currentTimeMillis() && this.authenticationTime > System.currentTimeMillis() - AUTHENTICATION_TIME) {
            this.authenticated.set(true);
            Log.INFO.log("Connection from " + this.getConnectionName() + " authenticated");
            this.localMachine.registerSocket(this);
            if (this.otherAuthenticated.get()) {
                this.postAuth.forEach(consumer -> consumer.accept(this));
                this.postAuth.clear();
            }
        } else {
            Log.WARN.log("Connection from " + this.getConnectionName() + " failed authentication");
            this.close();
        }
    }

    /**
     * Hashes for authentication the arguments along with the {@link Config#authenticationKey}.
     *
     * @param hashRequest the random sequence of bytes to hash with.
     * @param machineId   the machine id to hash with.
     * @param time        the millisecond representation of time to hash with.
     * @return the hash of the parameters and the {@link Config#authenticationKey}
     */
    private static byte[] getHash(byte[] hashRequest, String machineId, long time) {// with integrated time
        byte[] machineIdBytes = machineId.getBytes(Charset.forName("UTF-8"));
        byte[] keyBytes = Config.authenticationKey.getBytes(Charset.forName("UTF-8"));
        byte[] digestion = new byte[keyBytes.length + hashRequest.length + machineIdBytes.length + Long.BYTES];
        System.arraycopy(hashRequest, 0, digestion, 0, hashRequest.length);
        System.arraycopy(keyBytes, 0, digestion, hashRequest.length, keyBytes.length);
        System.arraycopy(machineIdBytes, 0, digestion, hashRequest.length + keyBytes.length, machineIdBytes.length);
        System.arraycopy(ObjectSerialization.serialize(Long.class, time), 0, digestion, hashRequest.length + keyBytes.length + machineIdBytes.length, Long.BYTES);
        for (int i = 0; i < Config.hashingRounds; i++) digestion = DIGEST.digest(digestion);
        return digestion;// hashes the digestion
    }

    public void registerAuthenticationAction(Consumer<TransferSocket> consumer) {
        if (this.otherAuthenticated.get() && this.authenticated.get()) consumer.accept(this);
        else this.postAuth.add(consumer);
    }

    @Override
    public int compareTo(TransferSocket o) {
        return this.machinePriority.get() - o.machinePriority.get();
    }
}
