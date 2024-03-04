package pt.ulisboa.tecnico.hdsledger.utilities;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
// Utils to signature verification
// Uses some code logic of the RSAKeyGenerator
public class RSASignature {

    // check if there is a better way, to not have 2 getFunctions
    // TODO
    public static PrivateKey getPrivateKey(String privKeyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privEncoded;
        try (FileInputStream fis = new FileInputStream(privKeyPath)) {
            privEncoded = new byte[fis.available()];
            fis.read(privEncoded);
        }

        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFacPriv = KeyFactory.getInstance("RSA");

        return keyFacPriv.generatePrivate(privSpec);
    }

    public static PublicKey getPublicKey(String pubKeyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] pubEncoded;
        try (FileInputStream fis = new FileInputStream(pubKeyPath)) {
            pubEncoded = new byte[fis.available()];
            fis.read(pubEncoded);
        }

        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFacPub = KeyFactory.getInstance("RSA");

        return keyFacPub.generatePublic(pubSpec);
    }

//    public static byte[] sign(String message, PrivateKey privateKey) throws Exception {
//        Signature privateSignature = Signature.getInstance("SHA256withRSA");
//        privateSignature.initSign(privateKey);
//        privateSignature.update(message.getBytes(UTF_8));
//
//        //byte[] signature = privateSignature.sign();
//
//        return privateSignature.sign();
//    }

    public static byte[] sign(String message, String privKeyPath) throws Exception {


        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashedData = digest.digest(message.getBytes(StandardCharsets.UTF_8));

        PrivateKey privateKey = getPrivateKey(privKeyPath);

        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(hashedData);

        return privateSignature.sign();
    }

    public static boolean verifySign(String message, byte[] signature, String pubKeyPath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashedData = digest.digest(message.getBytes(StandardCharsets.UTF_8));

        PublicKey publicKey = getPublicKey(pubKeyPath);

        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(hashedData);

        // returns boolean
        return publicSignature.verify(signature);
    }
}