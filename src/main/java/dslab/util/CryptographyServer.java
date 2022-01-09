package dslab.util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CryptographyServer {

    private PrivateKey privateKey;
    private String componentId;

    private SecretKey secretKey;
    private IvParameterSpec iv;

    public CryptographyServer(String componentId) {
        this.componentId = componentId;
        loadPrivateKey();
    }

    private void loadPrivateKey() {
        byte[] privateKeyBytes;
        try {
            privateKeyBytes = Files.readAllBytes(Paths.get("keys\\server\\" + componentId + ".der"));
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    public String decryptRSA(String challenge) {
        byte[] decryptedChallengeBytes;
        try {
            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
            decryptedChallengeBytes = decryptCipher.doFinal(decode(challenge));

            return new String(decryptedChallengeBytes, StandardCharsets.US_ASCII);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        return null; // TODO error handling ?
    }

    public byte[] decode(String data){
        return Base64.getDecoder().decode(data);
    }

    public static SecretKey getKeyFromMessage(String msg, String salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(msg.toCharArray(), salt.getBytes(), 65536, 256);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec)
                .getEncoded(), "AES");
        return secret;
    }

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

    public void setSecretKey(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public void setIv(IvParameterSpec iv) {
        this.iv = iv;
    }

}
