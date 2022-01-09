package dslab.util;

import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
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
public class Cryptography {

    private SecretKey secretKey;



    public Cryptography() {
    }

    public SecretKey generateKey(int n){
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(n);
            SecretKey key = keyGenerator.generateKey();
            return key;
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        return null;
    }

    public static SecretKey getKeyFromMessage(String msg, String salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(msg.toCharArray(), salt.getBytes(), 65536, 256);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec)
                .getEncoded(), "AES");
        return secret;
    }

    public String encrypt(String input, SecretKey key) {

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] cipherText = cipher.doFinal(input.getBytes());
            return Base64.getEncoder()
                    .encodeToString(cipherText);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public String decrypt(String input, SecretKey key) {

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] plainText = cipher.doFinal(Base64.getDecoder()
                    .decode(input));

            return new String(plainText);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }




    /*


    private void loadKeys() {

    }

    private byte[] generateChallenge() {
        return null;
    }




    public String encode(byte[] data){
        return Base64.getEncoder().encodeToString(data);
    }
    public byte[] decode(String data){
        return Base64.getDecoder().decode(data);
    }

    public String encrypt (String message){
        byte[] messageInBytes = message.getBytes();
        try{
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(messageInBytes);
            return encode(encryptedBytes);
            
        }catch(Exception e){

        }
        return null;

    }

    public String decrypted(String encryptedMessage){
        byte[] encryptedBytes = decode(encryptedMessage);
        try{
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedMessage = cipher.doFinal(encryptedBytes);
            return new String(decryptedMessage, StandardCharsets.UTF_8);

        }catch(Exception e){

        }
        return null;
    }

    public static void main(String[] args) {
        //loadPublicKey("mailbox-earth-planet");
        Cryptography c = new Cryptography();
        try{
            String encryptedMessage = c.encrypt("Hello World");
            String decryptedMessage = c.decrypted(encryptedMessage);

            System.out.println(encryptedMessage);
            System.out.println(decryptedMessage);

        }catch(Exception e){

        }
    }

     */
}
