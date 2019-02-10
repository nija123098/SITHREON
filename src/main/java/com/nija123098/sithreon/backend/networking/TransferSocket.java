package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.util.ByteHandler;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.StringUtil;
import com.nija123098.sithreon.backend.util.ThreadMaker;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonSecurityException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A wrapper for a {@link Socket} which manages sends {@link MachineAction} commands.
 * <p>
 * Authentication does not indicate non-repudiation nor
 * does the authentication system guarantee functioning authentication.
 *
 * @author nija123098
 */
public class TransferSocket implements Comparable<TransferSocket> {

    /**
     * The time limit allowed for authentication.
     */
    private static final long AUTHENTICATION_TIME = 15_000;

    /**
     * A {@link SecureRandom} to generate challenge .
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * The {@link Cipher} to decrypt messages sent to this connection.
     */
    private static final Cipher DECRYPTION_CIPHER;

    /**
     * The set of one use authentication codes.
     */
    private final Set<String> CODES = new TreeSet<>();

    /**
     * Gets a single use authentication code.
     *
     * @return the code.
     */
    public String getOneUseAuthenticationCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = StringUtil.base64EncodeOneLine(bytes);
        if (!CODES.add(code)) return getOneUseAuthenticationCode();
        return code;
    }

    static {
        try {
            DECRYPTION_CIPHER = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            if (Config.privateKey != null) DECRYPTION_CIPHER.init(Cipher.DECRYPT_MODE, Config.privateKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
            Log.ERROR.log("Invalid security settings", e);
            throw new NoReturnException();
        }
    }

    /**
     * The {@link Cipher} to encrypt with.
     */
    private final Cipher encryptionCipher;

    /**
     * The challenge bytes for authentication.
     */
    private final byte[] challenge;

    /**
     * The time to compare for authentication for time out due to no authentication.
     */
    private final long authenticationStartTime = System.currentTimeMillis();

    /**
     * If the {@link Machine} is a higher server then {@link Machine}.
     */
    private final boolean masterServerSide;

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
     * The actions to preform when a certificate is received.
     */
    private final Map<BigInteger, List<Runnable>> certificateReceiveActions = new ConcurrentHashMap<>();

    /**
     * The certificate the other side has claimed to be or has been identified as.
     */
    private final AtomicReference<Certificate> otherCertificate = new AtomicReference<>();

    /**
     * The allowed machine actions the other side of the socket is allowed to use.
     */
    private final EnumSet<MachineAction> allowedMachineActions = EnumSet.noneOf(MachineAction.class);

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
        this(localMachine, new Socket(host, port), true);
    }

    /**
     * Constructs a wrapper for a socket that has been accepted by a server.
     *
     * @param localMachine the {@link Machine} this instance belongs to.
     * @param socket       the accepted socket from a {@link SocketAcceptor}.
     * @throws IOException if flushing or a stream throws an {@link IOException}.
     */
    TransferSocket(Machine localMachine, Socket socket, boolean masterServerSide) throws IOException {
        this.masterServerSide = masterServerSide;
        this.localMachine = localMachine;
        this.socket = socket;
        this.outputStream = this.socket.getOutputStream();
        this.outputStream.flush();
        this.inputStream = this.socket.getInputStream();
        try {
            this.encryptionCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.ERROR.log("Invalid security settings", e);
            throw new NoReturnException();
        }
        this.localMachine.runOnClose(this::close);
        if (Config.authenticateMachines) {
            this.challenge = new byte[32];
            RANDOM.nextBytes(this.challenge);// This must occur before reading MachineActions
            this.write(MachineAction.IDENTIFY_SELF, Config.selfCertificateSerial);
        } else {
            this.challenge = null;
            this.write(MachineAction.AFFIRM_NO_AUTHENTICATION, Config.priority);
        }
        ThreadMaker.getThread(ThreadMaker.NETWORK, "Transfer Socket Read Thread " + this.getConnectionName(), true, () -> {// constantly reads
            boolean waitForMore = true;
            int readSize, packageSize = -1;
            byte[] buffer = new byte[1024];
            ByteHandler pendingBytes = new ByteHandler();
            MachineAction machineAction;
            Object[] machineActionArgs;
            while (!this.closed.get()) {
                try {
                    if (waitForMore) {// Wait for more data to complete a processing
                        Log.TRACE.log("Waiting for more bytes from " + this.getConnectionName() + " with " + pendingBytes.size() + " extra bytes");
                        readSize = this.inputStream.read(buffer);
                        if (readSize == -1) {
                            this.close();
                            return;
                        }
                        pendingBytes.add(buffer, readSize);
                    }
                    if (packageSize == -1) {// Get the processing size
                        if (pendingBytes.size() > Integer.BYTES) {
                            packageSize = ObjectSerialization.deserialize(Integer.class, pendingBytes.getBytes(true, Integer.BYTES));
                            pendingBytes.ensureCapacity(packageSize);
                        } else {
                            waitForMore = true;
                            continue;
                        }
                    }
                    if (pendingBytes.size() >= packageSize) {
                        machineAction = MachineAction.values()[pendingBytes.get(0)];// the first byte determines the action
                        if (machineAction.requiresAuthentication()) {
                            if (!this.authenticated.get()) {// Basic authentication check
                                Log.WARN.log("Connection from " + this.getConnectionName() + " attempted using authentication required MachineAction " + machineAction + " before being authenticated, dropping connection.");
                                this.close();
                                return;
                            }// Best not to forget this part.
                            if (!this.allowedMachineActions.contains(machineAction)) {// Check specific permissions
                                Log.WARN.log("Connection from " + this.getConnectionName() + " attempted using MachineAction " + machineAction + " but does not have permssion to, dropping connection.");
                                this.close();
                                return;
                            }
                        }
                        machineActionArgs = machineAction.read(this, pendingBytes);
                        if (machineActionArgs == null) continue;// args incomplete, wait for more data
                        Log.DEBUG.log("Received MachineAction of type " + machineAction + " from " + this.getConnectionName());
                        machineAction.act(this, machineActionArgs);
                        packageSize = -1;
                        waitForMore = pendingBytes.isEmpty();
                    } else waitForMore = true;
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
                        case "Software caused connection abort: recv failed":
                            Log.WARN.log("Software connection abort: recv failed, socket restart required");
                            this.close();
                        default:
                            Log.WARN.log("Unable to handle SocketException, continuing as normal", e);
                            break;
                    }
                } catch (IOException e) {
                    Log.WARN.log("IOException receiving object", e);
                }
            }
        }).start();
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
            Log.DEBUG.log("Flushed MachineAction of type " + action + " to " + this.getConnectionName());
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
        synchronized (this) {
            this.outputStream.write(ObjectSerialization.serialize(Integer.class, bytes.length));
            this.outputStream.write(bytes);
            this.outputStream.flush();
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
        if (this.closed.getAndSet(true)) return;// Atomic
        this.getLocalMachine().deregisterSocket(this);
        if (this.onCloseReference.get() != null) this.onCloseReference.get().run();
        try {
            this.inputStream.close();
            this.outputStream.close();
            this.socket.close();
        } catch (IOException e) {
            Log.ERROR.log("IOException closing socket to " + this.getConnectionName(), e);
        }
        if (this.masterServerSide) {
            Log.WARN.log("Closing machine due to closing connection to higher server");
            this.getLocalMachine().close();
        } else {
            Log.INFO.log("Closed socket to " + this.getConnectionName());
        }
    }

    public boolean isClosed() {
        return this.closed.get();
    }

    @Action(MachineAction.AFFIRM_NO_AUTHENTICATION)
    public void affirmNoAuthentication(Integer priority) {
        if (this.authenticated.get()) return;
        this.machinePriority.set(priority);
        if (Config.authenticateMachines) {
            Log.WARN.log("Machine " + this.getConnectionName() + " requested an affirmation of no authentication with authentication required");
            this.close();
        } else {
            this.allowedMachineActions.addAll(EnumSet.allOf(MachineAction.class));
            this.authenticateThis();
            this.authenticateOther();
            Log.INFO.log("Connection from " + this.getConnectionName() + " connected through mutual affirmation of no authentication");
        }
    }

    /**
     * Uses the temporary authentication code granted by the other side to gain authentication
     * and the permissions provided by {@link TransferSocket#authenticateWithTemporaryCode(String)}.
     *
     * @param code the code.
     */
    public void useTemporaryCodeAuthentication(String code) {
        this.allowedMachineActions.add(MachineAction.READY_TO_SERVE);
        this.authenticateThis();
        this.write(MachineAction.AUTHENTICATE_WITH_TEMPORARY_CODE, code);
        Log.TRACE.log("Authenticating with code " + code.length() + " characters log: " + code);
        this.authenticateOther();
        this.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_RUNNER);
    }

    @Action(MachineAction.AUTHENTICATE_WITH_TEMPORARY_CODE)
    public void authenticateWithTemporaryCode(String code) {
        if (CODES.remove(code)) {
            this.allowedMachineActions.addAll(EnumSet.allOf(MachineAction.class));
            this.authenticateThis();// todo restrict permissions on this, a lot.
            this.authenticateOther();
        } else {
            Log.WARN.log("Temporary code authentication failed, received " + code.length() + " characters: " + code);
            this.close();
        }
    }

    @Action(MachineAction.REQUEST_CERTIFICATE)
    public void requestCertificate(BigInteger serial) {
        this.write(MachineAction.SEND_CERTIFICATE, Certificate.getCertificate(serial));
    }

    @Action(MachineAction.SEND_CERTIFICATE)
    public void sendCertificate(Certificate certificate) {
        // Certificate is registered in constructor of Certificate, so no action with it directly is required
        this.certificateReceiveActions.remove(certificate.getSerialNumber()).forEach(Runnable::run);
    }

    @Action(MachineAction.IDENTIFY_SELF)
    public void identifySelf(BigInteger otherCertificateSerial) {// sending the serial number makes non-super server
        if (this.otherCertificate.get() != null) {
            Log.WARN.log("Machine " + this.getConnectionName() + " attempted to re-identify it's self");
            this.close();
            return;
        }
        if (otherCertificateSerial.equals(Config.selfCertificateSerial)) {// machines not required to hold their own cert
            Log.WARN.log("Opposite claims to be this machine.  Even if this were correct it would be insecure.  Consider disabling authentication in the config for testing or passing configs by arguments.  Disconnecting.");
            this.close();
            return;
        }
        BigInteger certificateSerial = otherCertificateSerial;
        Certificate current;
        while (certificateSerial != null) {
            if ((current = Certificate.getCertificate(certificateSerial)) == null) {
                this.certificateReceiveActions.computeIfAbsent(certificateSerial, bigInteger -> new ArrayList<>()).add(() -> identifySelf(otherCertificateSerial));
                return;
            }
            certificateSerial = current.getParentSerialNumber();
        }
        Certificate otherCertificate = Certificate.getCertificate(otherCertificateSerial);
        try {
            otherCertificate.check();
        } catch (SithreonSecurityException e) {
            Log.WARN.log("Untrusted certificate chain for claim of " + otherCertificate.getSubjectName() + ", closing connection", e);
            this.close();
            return;
        }
        try {
            this.encryptionCipher.init(Cipher.ENCRYPT_MODE, otherCertificate.getPublicKey());
        } catch (InvalidKeyException e) {
            Log.WARN.log("Machine claiming to be " + otherCertificate.getSubjectName() + " had invalid key, closing connection", e);
        }
        this.otherCertificate.set(otherCertificate);
        try {
            this.write(MachineAction.REQUEST_AUTHENTICATION, this.encryptionCipher.doFinal(this.challenge), Config.priority);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.ERROR.log("Unable to encrypt challenge", e);
        }
    }

    /**
     * Responds with the request for authentication by unencrypted and re-encrypting the challenge bytes,
     * showing it can decipher the text, thus is represented by the certificate it identified.
     * Re-encryption then keeps the challenge secret to prevent man in the middle attacks.
     *
     * @param encryptedChallenge the random byte sequence to hash with.
     * @param priority           the other machine priority to hash with.
     * @see MachineAction#REQUEST_AUTHENTICATION
     */
    @Action(MachineAction.REQUEST_AUTHENTICATION)
    public void requestAuthentication(byte[] encryptedChallenge, Integer priority) {
        if (Config.privateKey == null) {// no way to decrypt, cipher not initialized
            Log.ERROR.log("Unable to authenticate without a private key");
        }
        this.machinePriority.set(priority);
        try {
            this.write(MachineAction.AUTHENTICATE, this.encryptionCipher.doFinal(DECRYPTION_CIPHER.doFinal(encryptedChallenge)), true);
            this.authenticateOther();
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.WARN.log("Machine claiming to be " + this.otherCertificate.get().getSubjectName() + " failed authentication early", e);
            this.close();
        }
    }

    /**
     * Authenticates the opposite machine by checking the encrypted challenge bytes.
     *
     * @param encryptedChallengeResponse the hash the other machine has computed.
     * @param authenticate               if the machine wants to authenticate.
     * @see MachineAction#AUTHENTICATE
     */
    @Action(MachineAction.AUTHENTICATE)
    public void authenticate(byte[] encryptedChallengeResponse, Boolean authenticate) {// authenticate here is for not confusing var args
        try {
            if (this.authenticationStartTime < System.currentTimeMillis() - AUTHENTICATION_TIME) {
                Log.WARN.log("Connection from " + this.getConnectionName() + " failed authentication with time out, " + (System.currentTimeMillis() - this.authenticationStartTime) + "ms too long");
            } else if (!authenticate) {
                Log.WARN.log("Connection from " + this.getConnectionName() + " failed authentication with authentication wish being false");
            } else if (!Arrays.equals(DECRYPTION_CIPHER.doFinal(encryptedChallengeResponse), this.challenge)) {
                Log.WARN.log("Connection from " + this.getConnectionName() + " failed authentication with incorrect challenge response");
            } else {
                this.allowedMachineActions.addAll(this.otherCertificate.get().allowedActions());
                this.authenticateThis();
                Log.INFO.log("Connection from " + this.getConnectionName() + " authenticated");
                return;
            }
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            Log.WARN.log("Connection from " + this.getConnectionName() + " failed authentication with bad padding", e);
        }
        this.close();
    }

    /**
     * Registers an action to be done when this socket is authenticated,
     * or immediately if already authenticated.
     *
     * @param consumer the action to do when authenticated.
     */
    public void registerAuthenticationAction(Consumer<TransferSocket> consumer) {
        if (this.otherAuthenticated.get() && this.authenticated.get()) consumer.accept(this);
        else this.postAuth.add(consumer);
    }

    /**
     * Processes when the other is proven to be authentic.
     */
    private void authenticateThis() {
        if (this.allowedMachineActions.isEmpty())
            Log.ERROR.log("Authenticated but no machine actions have been allowed to other side.");
        this.authenticated.set(true);
        if (this.otherAuthenticated.get()) this.mutualAuthentication();
    }

    /**
     * Processes when this connection is proven to the other side to be authentic.
     */
    private void authenticateOther() {
        this.otherAuthenticated.set(true);
        if (this.authenticated.get()) this.mutualAuthentication();
    }

    /**
     * Processes when both sides know that each other are authentic.
     */
    private void mutualAuthentication() {
        this.certificateReceiveActions.clear();
        this.localMachine.registerSocket(this);
        this.postAuth.forEach(consumer -> consumer.accept(this));
        this.postAuth.clear();
    }

    @Override
    public int compareTo(TransferSocket o) {
        return this.machinePriority.get() - o.machinePriority.get();
    }
}
