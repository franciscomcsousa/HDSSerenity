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

    private static String getPrivatePath(String stringId)
    {
        return "../KeyInfrastructure/id" + stringId + ".key";
    }

    private static String getPublicPath(String stringId)
    {
        return "../KeyInfrastructure/id" + stringId + ".key.pub";
    }

    private static PrivateKey getPrivateKey(String stringId) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privEncoded;
        String privKeyPath = getPrivatePath(stringId);
        try (FileInputStream fis = new FileInputStream(privKeyPath)) {
            privEncoded = new byte[fis.available()];
            fis.read(privEncoded);
        }

        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFacPriv = KeyFactory.getInstance("RSA");

        return keyFacPriv.generatePrivate(privSpec);
    }

    private static PublicKey getPublicKey(String stringId) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] pubEncoded;
        String pubKeyPath = getPublicPath(stringId);

        try (FileInputStream fis = new FileInputStream(pubKeyPath)) {
            pubEncoded = new byte[fis.available()];
            fis.read(pubEncoded);
        }

        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFacPub = KeyFactory.getInstance("RSA");

        return keyFacPub.generatePublic(pubSpec);
    }

    /**
     * Creates a signature of the given data
     *
     * @param signable data to sign
     * @param stringId authout of the signature
     * @return byte[] - signature of the data
     * @throws Exception exception
     */
    public static byte[] sign(String signable, String stringId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashedData = digest.digest(signable.getBytes(StandardCharsets.UTF_8));

        PrivateKey privateKey = getPrivateKey(stringId);

        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(hashedData);

        return privateSignature.sign();
    }

    /**
     * Verifies if the given signature was created by the author and correspond to the signable object
     *
     * @param signable data which was originally signed
     * @param signature signature
     * @param stringId author of the signature
     * @return boolean - whether the signature matches the author's public key
     * @throws Exception exception
     */
    public static boolean verifySign(String signable, byte[] signature, String stringId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashedData = digest.digest(signable.getBytes(StandardCharsets.UTF_8));

        PublicKey publicKey = getPublicKey(stringId);

        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(hashedData);

        // returns boolean
        return publicSignature.verify(signature);
    }
}