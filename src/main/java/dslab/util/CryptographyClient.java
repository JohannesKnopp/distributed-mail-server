package dslab.util;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class CryptographyClient {

    private String componentId;

    private PublicKey publicKey;
    private byte[] challenge;
    private byte[] encryptedChallenge;
    private String encodedEncryptedChallenge;

    private SecretKey secretKey;
    private byte[] byteIv;
    private IvParameterSpec iv;

    private Cipher cipher;

    public CryptographyClient(String componentId) {
        try {
            this.componentId = componentId;
            this.cipher = Cipher.getInstance("RSA");

            loadPublicKey();
            generateChallenge();
            encryptChallenge();
            encodeEncryptedChallenge();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPublicKey() throws Exception {

            byte[] publicKeyBytes = Files.readAllBytes(Paths.get("keys\\client\\" + componentId + "_pub.der"));
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

    }

    private void generateChallenge() {
        try {
            byte[] challenge = new byte[32];
            SecureRandom.getInstanceStrong().nextBytes(challenge);
            this.challenge = challenge;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void encryptChallenge() {
        try {
           encryptedChallenge = cipher.doFinal(challenge);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void encodeEncryptedChallenge() {
        try {
            encodedEncryptedChallenge = Base64.getEncoder().encodeToString(encryptedChallenge);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String encryptRSAEncoded(String message) {
        try {
            byte[] messageBytes = message.getBytes();
            return Base64.getEncoder().encodeToString(cipher.doFinal(messageBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] getEncryptedChallenge() {
        return encryptedChallenge;
    }

    public String getEncodedEncryptedChallenge() {
        return encodedEncryptedChallenge;
    }

    //TODO AES

    public void generateKey(){
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(128);
            secretKey = keyGenerator.generateKey();
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
    }

    /*public static SecretKey getKeyFromMessage(String msg, String salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(msg.toCharArray(), salt.getBytes(), 65536, 256);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec)
                .getEncoded(), "AES");
        return secret;
    }*/

    public String encrypt(String input, SecretKey key, IvParameterSpec iv) {

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] cipherText = cipher.doFinal(input.getBytes());
            return Base64.getEncoder()
                    .encodeToString(cipherText);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public String decrypt(String input, SecretKey key, IvParameterSpec iv) {

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] plainText = cipher.doFinal(Base64.getDecoder()
                    .decode(input));

            return new String(plainText);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public String encryptMessage(String message) {
        return encrypt(message, secretKey, iv);
    }

    public String decryptMessage(String message) {
        return decrypt(message, secretKey, iv);
    }

    public void generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        byteIv = iv;

        this.iv = new IvParameterSpec(iv);
    }

    public String encodedIv() {
       return Base64.getEncoder().encodeToString(byteIv);
    }

    public String encodedSecretKey() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    public byte[] getChallenge() {
        return challenge;
    }

    public String getChallengeString() {
        return new String(challenge, StandardCharsets.US_ASCII);
    }

    public String getIv() {
        return new String(byteIv, StandardCharsets.US_ASCII);
    }

    public String getSecretKey() {
        return new String(secretKey.getEncoded(), StandardCharsets.US_ASCII);
    }

    public String getChallengeStringEncoded() {
       return Base64.getEncoder().encodeToString(challenge);
    }

    public String getIvEncoded() {
        return Base64.getEncoder().encodeToString(byteIv);
    }

    public String getSecretKeyEncoded() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

}
