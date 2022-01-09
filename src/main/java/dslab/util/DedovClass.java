package dslab.util;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class DedovClass {

    private PublicKey publicKey;
    private PrivateKey privateKey;

    public DedovClass(String componentId) {

    }

    public static void loadPublicKey(String componentId) {
        try {
            byte[] keyBytes = Files.readAllBytes(Paths.get("keys\\client\\" + componentId + "_pub.der"));

            System.out.println(Arrays.toString(keyBytes));

            byte[] challenge = new byte[32];
            SecureRandom.getInstanceStrong().nextBytes(challenge);

            System.out.println(Arrays.toString(challenge));

            Cipher encryptCipher = Cipher.getInstance("RSA");
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

            String secretMessage = "Stefan Aufmuth";
            byte[] secretMessageBytes = secretMessage.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedMessageBytes = encryptCipher.doFinal(secretMessageBytes);

            String encodedMessage = Base64.getEncoder().encodeToString(encryptedMessageBytes);

            byte[] privateKeyBytes = Files.readAllBytes(Paths.get("keys\\server\\" + componentId + ".der"));
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decryptedMessageBytes = decryptCipher.doFinal(encryptedMessageBytes);
            String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

            System.out.println(decryptedMessage);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {

            e.printStackTrace();
        } catch (InvalidKeySpecException e) {

            e.printStackTrace();
        } catch (NoSuchPaddingException e) {

            e.printStackTrace();
        } catch (InvalidKeyException e) {

            e.printStackTrace();
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        loadPublicKey("mailbox-earth-planet");
    }

}
