package dslab.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TestMailClient {

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    public void startConnection(String ip, int port) {
        try {
            clientSocket = new Socket(ip, port);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (Exception e) {

        }
    }

    public void sendMessage(String msg) {
        try {
            out.println(msg);
        } catch (Exception e) {

        }
    }

    public String recvMessage() throws Exception {
        return in.readLine();
    }


    public void stopConnection() throws Exception{
        in.close();
        out.close();
        clientSocket.close();
    }

    public static void main(String[] args) throws Exception{

        TestMailClient client = new TestMailClient();
        client.startConnection("localhost", 12123);
        client.recvMessage();

        client.sendMessage("startsecure");
        String componentId = client.recvMessage().split("\\s")[1];
        CryptographyClient crypto = new CryptographyClient(componentId);
        // ok <client-challenge> <secret-key> <iv>
        crypto.generateKey();
        crypto.generateIv();
        String challenge = crypto.getEncodedEncryptedChallenge();
        String secretKey = crypto.encodedSecretKey();
        String iv = crypto.encodedIv();
        client.sendMessage("ok " + challenge + " " + secretKey + " " + iv);
        String result = client.recvMessage().substring(3);
        System.out.println(result);
        System.out.println(new String(crypto.getChallenge(), StandardCharsets.US_ASCII));
        client.sendMessage(crypto.encryptMessage("ok"));

        String message = "login trillian 12345";
        System.out.println(crypto.encryptMessage(message));
        client.sendMessage(crypto.encryptMessage(message));

        System.out.println(crypto.decryptMessage(client.recvMessage()));

    }
}
