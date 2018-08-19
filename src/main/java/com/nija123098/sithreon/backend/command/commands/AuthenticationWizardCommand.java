package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;
import com.nija123098.sithreon.backend.networking.Certificate;
import com.nija123098.sithreon.backend.networking.MachineAction;
import com.nija123098.sithreon.backend.networking.ObjectSerialization;
import com.nija123098.sithreon.backend.util.FileUtil;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.StringUtil;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import sun.security.provider.X509Factory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Assists in making certificates for the authentication system.
 */
public class AuthenticationWizardCommand extends Command {
    public AuthenticationWizardCommand() {
        super("authorization", "wizard");
        this.registerAlias("authw");
    }

    @CommandMethod
    public void command(Scanner scanner) throws IOException {
        PrintStream out = System.out;
        if (Config.selfCertificateSerial == null) {
            out.println("No certificate set up here, setting up root.");
            // Keys
            KeyPair keyPair = guidedGenerateKeyPair(scanner, out);
            Config.privateKey = keyPair.getPrivate();
            out.println("Adding private key to config file.");
            Files.write(Config.CONFIG_PATH, Collections.singletonList("privateKey=" + StringUtil.base64EncodeOneLine(Config.privateKey.getEncoded())), StandardOpenOption.APPEND);
            // Root Certificate
            Certificate rootCertificate = guidedGenerateRootCertificate(scanner, out, keyPair.getPublic());
            rootCertificate.setTrust(true);
            Config.selfCertificateSerial = rootCertificate.getSerialNumber();
            out.println("Adding certificate to KeyStore.");
            FileUtil.modifyKeyStore(Config.trustedCertificateStorage, Config.keyStorePassword, keyStore -> {
                try {
                    keyStore.setCertificateEntry(rootCertificate.getSubjectName(), rootCertificate.getCertificate());
                } catch (KeyStoreException e) {
                    throw new SithreonException("Unable to write certificate to keystore", e);
                }
            });
            out.println("Setting certificate serial in config.");
            Files.write(Config.CONFIG_PATH, Collections.singletonList("selfCertificateSerial=" + rootCertificate.getSerialNumberString()), StandardOpenOption.APPEND);
        }
        while (true) {
            out.println("Would you like to make a machine certificate?");
            if (!Config.getFunction(Boolean.class).apply(scanner.nextLine())) return;
            PublicKey publicKey;
            out.println("Do you have a public key already to sign for the certificate?");
            if (Config.getFunction(Boolean.class).apply(scanner.nextLine())) {
                out.println("Please provide the public key in base 64 with no new lines.");
                publicKey = Config.getFunction(PublicKey.class).apply(scanner.nextLine());
            } else {
                publicKey = guidedGenerateKeyPair(scanner, out).getPublic();
                out.println("The private key must be put in the machine's configuration file to authenticate properly.");
            }
            Certificate certificate = guidedGenerateCertificate(scanner, out, publicKey);
            out.println("Adding certificate to KeyStore for storage.");
            FileUtil.modifyKeyStore(Config.trustedCertificateStorage, Config.keyStorePassword, keyStore -> {
                try {
                    keyStore.setCertificateEntry(certificate.getSubjectName(), certificate.getCertificate());
                } catch (KeyStoreException e) {
                    throw new SithreonException("Unable to write certificate to KeyStore", e);
                }
            });
            out.println("The following certificate serial number for the using machine must be in the config file of the machine planning on using it under \"selfCertificateSerial\".");
            out.println(certificate.getSerialNumberString());
        }
    }

    private static KeyPair guidedGenerateKeyPair(Scanner scanner, PrintStream out) {
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new SithreonException("No provider of RSA algorithm", e);
        }
        out.println("Size of key?");
        keyPairGenerator.initialize(Config.getFunction(Integer.class).apply(scanner.nextLine()));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        out.println("Public key:");
        out.println(StringUtil.base64EncodeOneLine(keyPair.getPublic().getEncoded()));
        out.println("Private key:");
        out.println(StringUtil.base64EncodeOneLine(keyPair.getPrivate().getEncoded()));
        return keyPair;
    }

    private static Certificate guidedGenerateRootCertificate(Scanner scanner, PrintStream out, PublicKey receiver) {
        out.println("Validity time in days?");
        Long expireTime = TimeUnit.DAYS.toMillis(Config.getFunction(Integer.class).apply(scanner.nextLine())) + System.currentTimeMillis();
        out.println("Distinguished Name?");
        Certificate certificate = generateRootCertificate(expireTime, receiver, Config.privateKey, MachineAction.values(), scanner.nextLine());
        out.println("Root certificate:");
        out.println(X509Factory.BEGIN_CERT);
        out.println(StringUtil.base64EncodeOneLine(certificate.getBytes()));
        out.println(X509Factory.END_CERT);
        return certificate;
    }

    public static Certificate generateRootCertificate(Long expireDate, PublicKey publicReceiver, PrivateKey signingKey, MachineAction[] allowedMachineActions, String name) {
        try {
            JcaX509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(new X500Name(name), Certificate.getReadableSerialNumber(), new Date(), new Date(expireDate), new X500Name(name), publicReceiver);
            certGen.addExtension(new ASN1ObjectIdentifier(Config.certificatePermissionOID), true, ObjectSerialization.serialize(MachineAction[].class, allowedMachineActions));
            certGen.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            return Certificate.getCertificate(new JcaX509CertificateConverter().getCertificate(certGen.build(new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(signingKey))));
        } catch (CertIOException | OperatorCreationException | CertificateException e) {
            throw new SithreonException("Unable to generate certificate", e);
        }
    }

    private static Certificate guidedGenerateCertificate(Scanner scanner, PrintStream out, PublicKey receiver) {
        out.println("Validity time in days?");
        Long expireTime = TimeUnit.DAYS.toMillis(Config.getFunction(Integer.class).apply(scanner.nextLine())) + System.currentTimeMillis();
        out.println("Distinguished Name of receiver?");
        String distinguishedName = scanner.nextLine();
        out.println("Allowed MachineActions?");
        MachineAction[] actions = null;
        while (actions == null) {
            try {
                actions = Config.getFunction(MachineAction[].class).apply(scanner.nextLine());
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage());
            }
        }
        Certificate certificate = generateCertificate(Certificate.getCertificate(Config.selfCertificateSerial), expireTime, receiver, Config.privateKey, actions, distinguishedName);
        out.println("Certificate:");
        out.println(X509Factory.BEGIN_CERT);
        out.println(StringUtil.base64EncodeOneLine(certificate.getBytes()));
        out.println(X509Factory.END_CERT);
        return certificate;
    }

    public static Certificate generateCertificate(Certificate authority, Long expireDate, PublicKey publicReceiver, PrivateKey signingKey, MachineAction[] allowedMachineActions, String subjectName) {
        try {
            authority.check();
            if (authority.getCertificate().getNotAfter().getTime() < expireDate) {
                Log.WARN.log("Specified expire date is longer then authority expire date, reducing");
                expireDate = authority.getCertificate().getNotAfter().getTime();
            }
            X500Name issuerName = new X500Name(authority.getCertificate().getIssuerX500Principal().getName());
            JcaX509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(issuerName, Certificate.getReadableSerialNumber(), new Date(), new Date(expireDate), new X500Name(subjectName), publicReceiver);
            EnumSet<MachineAction> machineActions = EnumSet.noneOf(MachineAction.class);
            EnumSet<MachineAction> authorityActions = authority.allowedActions();
            for (MachineAction action : allowedMachineActions) {
                if (authorityActions.contains(action)) machineActions.add(action);
                else
                    Log.WARN.log("Unable to add " + action + " since the parent does not have this permission, removing");
            }
            certGen.addExtension(new ASN1ObjectIdentifier(Config.certificatePermissionOID), true, ObjectSerialization.serialize(MachineAction[].class, machineActions.toArray(new MachineAction[machineActions.size()])));
            certGen.addExtension(Extension.authorityKeyIdentifier, false, new AuthorityKeyIdentifier(new GeneralNames(new GeneralName(issuerName)), authority.getSerialNumber()));
            return Certificate.getCertificate(new JcaX509CertificateConverter().getCertificate(certGen.build(new JcaContentSignerBuilder("SHA256WithRSA").build(signingKey))));
        } catch (CertIOException | OperatorCreationException | CertificateException e) {
            throw new SithreonException("Unable to generate certificate", e);
        }
    }
}
