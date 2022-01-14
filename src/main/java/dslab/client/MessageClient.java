package dslab.client;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.entity.Message;
import dslab.util.Config;
import dslab.util.CryptographyClient;
import dslab.util.HashingUtil;
import dslab.util.Helper;

public class MessageClient implements IMessageClient, Runnable {

    private Config config;
    private String componentId;
    private String name;

    private InputStream in;
    private PrintStream out;
    private Shell shell;
    private BufferedReader userInputReader;

    private Socket dmapSocket;
    private BufferedReader dmapReader;
    private PrintWriter dmapWriter;
    private CryptographyClient crypto;


    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;

        shell = new Shell(this.in, this.out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        dmapSocket = null;
        userInputReader = null;

        try {
            dmapSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            dmapReader = new BufferedReader(new InputStreamReader(dmapSocket.getInputStream()));
            dmapWriter = new PrintWriter(dmapSocket.getOutputStream(), true);

            Helper.readAndVerify(dmapReader, "ok DMAP2.0");
            out.println("Client: " + config.getString("mailbox.user") + " is up! Enter command.");

            dmapWriter.println("startsecure");
            String cid = dmapReader.readLine().split("\\s")[1];
           // String[] wpa = getSecurity(cid);
            //String toEncrypt = "ok " + wpa[0] + " " + wpa[1] + " " + wpa[2];

            try {
                crypto = new CryptographyClient(cid);
            }catch (Exception e){
                shutdown();
            }
            // ok <client-challenge> <secret-key> <iv>
            crypto.generateKey();
            crypto.generateIv();

            String toEncrypt = "ok " + crypto.getChallengeStringEncoded() + " " + crypto.getSecretKeyEncoded() + " " + crypto.getIvEncoded();
            //System.out.println(toEncrypt);
            dmapWriter.println(crypto.encryptRSAEncoded(toEncrypt));
            //System.out.println(crypto.encryptRSAEncoded(toEncrypt));
            dmapWriter.flush();

            String result = crypto.decryptMessage(dmapReader.readLine()).substring(3);
            String expected =  new String(crypto.getChallenge(), StandardCharsets.US_ASCII);

            byte[] r = result.getBytes();
            byte[] e = expected.getBytes();

            boolean same = true;
            for (int i = 0; i < r.length; i++) {
                if (r[i] != e[i]) {
                    same = false;
                    break;
                }
            }

            if (!same) {
                out.println("Failed challenge");
                shutdown();
            }

            dmapWriter.println(crypto.encryptMessage("ok"));
            String message = "login " + config.getString("mailbox.user") + " " + config.getString("mailbox.password");
            dmapWriter.println(crypto.encryptMessage(message));
            dmapReader.readLine();

        } catch (UnknownHostException e) {
            System.out.println("Cannot connect to host: " + e.getMessage());
        } catch (SocketException e) {
            // when the socket is closed, the I/O methods of the Socket will throw a SocketException
            // almost all SocketException cases indicate that the socket was closed
            System.out.println("SocketException while handling socket: " + e.getMessage());
        } catch (IOException e) {
            // you should properly handle all other exceptions
            throw new UncheckedIOException(e);
        }

        shell.run();
    }

    public String[] getSecurity(String componentId){
        String[] out = new String[3];
        try {
            crypto = new CryptographyClient(componentId);
        }catch (Exception e){
            shutdown();
        }
        // ok <client-challenge> <secret-key> <iv>
        crypto.generateKey();
        crypto.generateIv();
        String challenge = crypto.getEncodedEncryptedChallenge();
        String secretKey = crypto.encodedSecretKey();
        String iv = crypto.encodedIv();
        out[0] = challenge;
        out[1] = secretKey;
        out[2] = iv;
        return out;
    }

    public String[] getData(String componentId) {
        String[] out = new String[3];
        try {
            crypto = new CryptographyClient(componentId);
        }catch (Exception e){
            shutdown();
        }
        // ok <client-challenge> <secret-key> <iv>
        crypto.generateKey();
        crypto.generateIv();
        String challenge = crypto.getEncodedEncryptedChallenge();
        String secretKey = crypto.encodedSecretKey();
        String iv = crypto.encodedIv();
        out[0] = challenge;
        out[1] = secretKey;
        out[2] = iv;
        return out;
    }

    @Override
    @Command
    public void inbox() {
        try {
            dmapWriter.println(crypto.encryptMessage("list"));
            boolean listDone = false;
            ArrayList<String> lines = new ArrayList<String>();
            while (!listDone) {
                String nextLine = crypto.decryptMessage(dmapReader.readLine());
                if (!nextLine.equals("ok")) {
                    lines.add(nextLine);
                } else {
                    listDone = true;
                }
            }
            lines.add("ok");
            for (String line : lines) {
                if (!line.equals("ok")) {
                    String number = line.split("\\s")[0];
                    dmapWriter.println(crypto.encryptMessage("show " + number));
                    out.println(number + ".");
                    boolean done = false;
                    while (!done) {
                        String lineContent = crypto.decryptMessage(dmapReader.readLine());
                        if (!lineContent.equals("ok")) {
                            out.println(lineContent);
                        } else {
                            done = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
        }
    }

    @Override
    @Command
    public void delete(String id) {
        try {
            dmapWriter.println(crypto.encryptMessage("delete " + id));
            out.println(crypto.decryptMessage(dmapReader.readLine()));
        } catch (IOException e) {
        }
    }

    @Override
    @Command
    public void verify(String id) {
        try {
            dmapWriter.println(crypto.encryptMessage("show " + id));

            Message message = new Message();
            message.setTo(new ArrayList<String>());

            boolean done = false;
            while (!done) {
                String lineContent = crypto.decryptMessage(dmapReader.readLine());
                if (lineContent.equals("ok")) {
                    done = true;
                } else if (lineContent.startsWith("error")) {
                    done = true;
                    out.println("error message not found");
                    return;
                } else {
                    if (lineContent.startsWith("from")) {
                        message.setFrom(extractDataFromLine(lineContent));
                    } else if (lineContent.startsWith("to")) {
                        String[] recipients = extractDataFromLine(lineContent).split(",");
                        for (String recipient : recipients) {
                            message.addTo(extractDataFromLine(recipient));
                        }
                    } else if (lineContent.startsWith("subject")) {
                        message.setSubject(extractDataFromLine(lineContent));
                    } else if (lineContent.startsWith("data")) {
                        message.setData(extractDataFromLine(lineContent));
                    } else if (lineContent.startsWith("hash")) {
                        if (!lineContent.equals("hash")) {
                            message.setHash(extractDataFromLine(lineContent));
                        } else {
                            message.setHash(null);
                        }
                    }
                }
            }

            if (message.getHash() != null) {
                String calculatedHash = HashingUtil.calculateEncodedHash(message);
                String actualHash = message.getHash();
                if (calculatedHash.equals(actualHash)) {
                    out.println("ok");
                } else {
                    out.println("error hashes are different");
                }
            } else {
                out.println("error no hash provided in the message");
            }
        } catch (Exception e) {
        }
    }

    public String extractDataFromLine(String line) {
        return line.substring(line.indexOf(" ") + 1);
    }

    @Override
    @Command
    public void msg(String to, String subject, String data) {
        Socket conn = null;
        BufferedReader reader = null;
        PrintWriter writer = null;

        String from = config.getString("transfer.email");
        ArrayList<String> toList = new ArrayList<String>();

        Message m = new Message();
        m.setTo(new ArrayList<String>());
        m.setFrom(from);

        String[] recipients = to.split(",");
        for (String recipient : recipients) {
            m.addTo(recipient);
        }

        m.setSubject(subject);
        m.setData(data);
        try {
            m.setHash(HashingUtil.calculateEncodedHash(m));
        } catch (Exception e) {
        }

        try {
            conn = Helper.waitForSocket(config.getString("transfer.host"), config.getInt("transfer.port"), 5000);

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            writer = new PrintWriter(conn.getOutputStream());

            Helper.readAndVerify(reader, "ok DMTP2.0");

            Helper.writeAndVerify(reader, writer, "begin", "ok");

            Helper.writeAndVerify(reader, writer, "from " + from, "ok");

            Helper.writeAndVerify(reader, writer, "data " + data, "ok");

            Helper.writeAndVerify(reader, writer, "subject " + subject, "ok");

            Helper.writeAndVerify(reader, writer, "to " + to, "ok " + to.split(",").length);

            Helper.writeAndVerify(reader, writer, "hash " + m.getHash(), "ok");

            Helper.writeAndVerify(reader, writer, "send", "ok");

            Helper.writeAndVerify(reader, writer, "quit", "ok bye");

        } catch (IOException e) {
        }

        out.println("ok");
    }

    @Override
    @Command
    public void shutdown() {
        if (dmapSocket != null && !dmapSocket.isClosed()) {
            try {
                dmapSocket.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }

        if (userInputReader != null) {
            try {
                userInputReader.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }
        shell.out().println("Shutting down...");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }


}