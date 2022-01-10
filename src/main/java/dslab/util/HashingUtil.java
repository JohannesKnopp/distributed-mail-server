package dslab.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import dslab.entity.Message;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class HashingUtil {

    public static String calculateEncodedHash(Message message) throws Exception {
        SecretKeySpec key = Keys.readSecretKey(new File("keys/hmac.key"));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        String preparedMessage = String.join("\n", message.getFrom(), message.getAllTos(), message.getSubject(), message.getData());
        byte[] messageBytes = preparedMessage.getBytes(StandardCharsets.US_ASCII);
        byte[] hashResult = mac.doFinal(messageBytes);
        return Base64.getEncoder().encodeToString(hashResult);
    }

}
