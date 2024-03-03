package pt.ulisboa.tecnico.hdsledger.utilities;

import java.io.FileInputStream;
import java.io.IOException;
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
    public static PrivateKey getPrivateKey(String privateKeyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privEncoded;
        try (FileInputStream fis = new FileInputStream(privateKeyPath)) {
            privEncoded = new byte[fis.available()];
            fis.read(privEncoded);
        }

        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFacPriv = KeyFactory.getInstance("RSA");

        return keyFacPriv.generatePrivate(privSpec);
    }

    public static PublicKey getPublicKey(String publicKeyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] pubEncoded;
        try (FileInputStream fis = new FileInputStream(publicKeyPath)) {
            pubEncoded = new byte[fis.available()];
            fis.read(pubEncoded);
        }

        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFacPub = KeyFactory.getInstance("RSA");

        return keyFacPub.generatePublic(pubSpec);
    }

    public static String sign(String plainText, PrivateKey privateKey) throws Exception {
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(plainText.getBytes(UTF_8));

        byte[] signature = privateSignature.sign();

        return Base64.getEncoder().encodeToString(signature);
    }

    public static boolean verifySign(String plainText, String signature, PublicKey publicKey) throws Exception {
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(plainText.getBytes(UTF_8));

        byte[] signatureBytes = Base64.getDecoder().decode(signature);

        return publicSignature.verify(signatureBytes);
    }
}