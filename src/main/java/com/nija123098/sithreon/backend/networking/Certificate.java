package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.util.FileUtil;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.StringUtil;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonSecurityException;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Certificate {
    /**
     * Map of all serial number {@link BigInteger}s from all received certificates mapped to the certificate instance.
     * <p>
     * Using hash would prevent malicious attacks to prevent authentication
     * using invalid certificates with known certificate serial numbers.
     */
    private static final Map<BigInteger, Certificate> CERTIFICATE_CACHE = new HashMap<>();

    static {
        loadKeystore(Config.trustedCertificateStorage, true);
    }

    /**
     * Loads a {@link KeyStore} of {@link X509Certificate}s and adds them to the known certificates.
     *
     * @param file  the {@link File} of the keystore.
     * @param trust if the certificates should be trusted inherently.
     */
    private static void loadKeystore(File file, boolean trust) {
        KeyStore keyStore = FileUtil.getLoadedKeyStore(file, Config.keyStorePassword);
        try {
            Enumeration<String> aliases = keyStore.aliases();
            String alias;
            while (aliases.hasMoreElements()) {
                alias = aliases.nextElement();
                X509Certificate x509Certificate = (X509Certificate) keyStore.getCertificate(alias);
                try {
                    Certificate.getCertificate(x509Certificate).trust.set(trust);
                } catch (SithreonSecurityException e) {
                    Log.INFO.log("Certificate " + alias + " expired", e);
                }
            }
        } catch (KeyStoreException e) {
            Log.ERROR.log("KeyStore unexpectedly not loaded " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Gets the instance based off of the {@link X509Certificate} instance.
     *
     * @param certificate the {@link X509Certificate} instance.
     * @return the instance representing the given {@link X509Certificate}.
     */
    public static Certificate getCertificate(X509Certificate certificate) {
        return CERTIFICATE_CACHE.computeIfAbsent(certificate.getSerialNumber(), serial -> new Certificate(certificate));
    }

    /**
     * Gets the instance based off of the binary or Base 64 with proper header and footer of a {@link X509Certificate} instance.
     *
     * @param bytes the bytes of the {@link X509Certificate} instance.
     * @return the instance representing the bytes of the represented {@link X509Certificate}.
     */
    public static Certificate getCertificate(byte[] bytes) {
        try {
            return getCertificate((X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(bytes)));
        } catch (CertificateException e) {
            Log.ERROR.log("Exception getting CertificateFactory", e);
            throw new NoReturnException();
        }
    }

    /**
     * Gets the instance off of the {@link BigInteger} serial number if it has it in the cache.
     *
     * @param serial the serial number of the certificate.
     * @return the instance represented by the serial number or null if the instance is not cached.
     */
    public static Certificate getCertificate(BigInteger serial) {
        return CERTIFICATE_CACHE.get(serial);
    }

    /**
     * Gets a Base 64 readable random 12 byte certificate serial number.
     *
     * @return a Base 64 readable serial number.
     */
    public static BigInteger getReadableSerialNumber() {
        byte[] bytes = new byte[12];
        new Random().nextBytes(bytes);
        return new BigInteger(Base64.getEncoder().encode(bytes));
    }

    /**
     * If the certificate is a trusted stored root certificate.
     *
     * Ignored if the certificate is not a root certificate.
     */
    private final AtomicBoolean trust = new AtomicBoolean();

    /**
     * The time the certificate will become invalid.
     */
    private final AtomicLong trustTime = new AtomicLong();

    /**
     * The wrapped {@link X509Certificate}.
     */
    private final X509Certificate certificate;

    /**
     * Constructs and puts into the cache.
     *
     * @param certificate the wrapped {@link X509Certificate}.
     */
    private Certificate(X509Certificate certificate) {
        this.certificate = certificate;
        CERTIFICATE_CACHE.put(this.getSerialNumber(), this);
    }

    /**
     * Checks if the certificate is invalid.
     *
     * @throws SithreonSecurityException if the certificate is invalid.
     */
    public void check() {
        if (this.trustTime.get() != 0) {
            if (this.trustTime.get() < System.currentTimeMillis()) {
                throw new SithreonSecurityException("Invalid timing for certificate");
            }
        }
        if (this.certificate == null) throw new SithreonSecurityException("Null certificate provided");
        if (!this.certificate.getPublicKey().getAlgorithm().equals("RSA"))
            throw new SithreonSecurityException("Non-RSA key used in certificate");
        if (this.certificate.getExtensionValue(Config.certificatePermissionOID) == null)
            throw new SithreonSecurityException("No certificate permissions exception");
        try {
            this.certificate.checkValidity();
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            throw new SithreonSecurityException("Invalid timing for certificate", e);
        }
        BigInteger bigInteger = this.getParentSerialNumber();
        PublicKey publicKey;
        Certificate parentCertificate = null;
        if (bigInteger == null) {
            publicKey = this.certificate.getPublicKey();
            if (!this.trust.get()) throw new SithreonSecurityException("Untrusted root certificate");
        } else {
            parentCertificate = CERTIFICATE_CACHE.get(bigInteger);
            if (parentCertificate == null) throw new SithreonSecurityException("No valid parent certificate");
            try {
                parentCertificate.check();
            } catch (SithreonSecurityException e) {
                throw new SithreonSecurityException("Invalid parent certificate", e);
            }
            if (((RSAPublicKey) this.certificate.getPublicKey()).getModulus().bitLength() < ((RSAPublicKey) parentCertificate.getPublicKey()).getModulus().bitLength())
                throw new SithreonSecurityException("RSA key not as long or longer then parent");
            if (!parentCertificate.allowedActions().containsAll(this.allowedActions())) {
                throw new SithreonSecurityException("Certificate contains extra permissions then parent");
            }
            publicKey = parentCertificate.certificate.getPublicKey();
        }
        try {
            this.certificate.verify(publicKey);
        } catch (CertificateException | NoSuchAlgorithmException | SignatureException | NoSuchProviderException e) {
            Log.ERROR.log("Something went wrong during certificate signature checking", e);
        } catch (InvalidKeyException e) {
            throw new SithreonSecurityException("Invalid signature");
        }
        this.trustTime.set(parentCertificate == null ? this.certificate.getNotAfter().getTime() : Math.min(this.certificate.getNotAfter().getTime(), this.certificate.getNotAfter().getTime()));
    }

    public void setTrust(boolean trust) {
        this.trust.set(trust);
    }

    /**
     * Gets an {@link EnumSet<MachineAction>} of the permissions allowed for this certificate.
     *
     * @return an {@link EnumSet<MachineAction>} of the permissions.
     */
    public EnumSet<MachineAction> allowedActions() {
        EnumSet<MachineAction> enumSet = EnumSet.noneOf(MachineAction.class);
        byte[] bytes = this.certificate.getExtensionValue(Config.certificatePermissionOID);
        enumSet.addAll(Arrays.asList(ObjectSerialization.deserialize(MachineAction[].class, Arrays.copyOfRange(bytes, 2, bytes.length))));
        return enumSet;
    }

    /**
     * Gets the wrapped {@link X509Certificate}.
     *
     * @return the wrapped {@link X509Certificate}.
     */
    public X509Certificate getCertificate() {
        return this.certificate;
    }

    /**
     * Gets the {@link BigInteger} serial number of the wrapped certificate.
     *
     * @return the {@link BigInteger} serial number of the wrapped certificate.
     */
    public BigInteger getSerialNumber() {
        return this.certificate.getSerialNumber();
    }

    /**
     * Gets the {@link BigInteger} serial number of the wrapped certificate.
     *
     * @return the {@link BigInteger} serial number of the wrapped certificate.
     */
    public BigInteger getParentSerialNumber() {
        byte[] bytes = this.certificate.getExtensionValue(Extension.authorityKeyIdentifier.getId());
        if (bytes == null) return null;
        return AuthorityKeyIdentifier.fromExtensions(new Extensions(new Extension(Extension.authorityKeyIdentifier, false, Arrays.copyOfRange(bytes, 2, bytes.length)))).getAuthorityCertSerialNumber();
    }

    /**
     * Gets an encoded form.
     *
     * @return an encoded form.
     */
    public byte[] getBytes() {
        try {
            return this.certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new SithreonSecurityException("Unable to serialize certificate", e);
        }
    }

    /**
     * Gets the distinguished subject name.
     *
     * @return the distinguished subject name.
     */
    public String getSubjectName() {
        return this.certificate.getSubjectDN().getName();
    }

    /**
     * Gets the {@link PublicKey} of the certificate.
     *
     * @return the {@link PublicKey} of the certificate.
     */
    public PublicKey getPublicKey() {
        return this.certificate.getPublicKey();
    }

    /**
     * Gets the base 64 representation of the bytes of the {@link BigInteger} representing the serial number.
     *
     * @return a base 64 representation.
     */
    public String getSerialNumberString() {
        return StringUtil.base64EncodeOneLine(this.getSerialNumber().toByteArray());
    }

    @Override
    public int hashCode() {
        return this.certificate.hashCode();
    }
}
