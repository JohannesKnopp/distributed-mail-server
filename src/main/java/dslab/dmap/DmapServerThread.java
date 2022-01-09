package dslab.dmap;

import dslab.entity.MailboxStorage;
import dslab.entity.Message;
import dslab.util.Config;
import dslab.util.CryptographyServer;
import dslab.util.Validator;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DmapServerThread extends Thread {

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private String componentId;
    private String acceptedDomain;
    private MailboxStorage mailboxStorage;
    private ConcurrentLinkedQueue<DmapClient> clients;
    private Config config;

    private boolean quit = false;

    public DmapServerThread(ServerSocket serverSocket, String componentId, MailboxStorage mailboxStorage, Config config) {
        this.serverSocket = serverSocket;
        this.componentId = componentId;
        this.mailboxStorage = mailboxStorage;
        this.config = config;
    }

    public void run() {
        clients = new ConcurrentLinkedQueue<>();
        acceptedDomain = config.getString("domain");
        executor = Executors.newCachedThreadPool();
        while (!quit) {
            try {
                DmapClient client = new DmapClient(serverSocket.accept());
                clients.add(client);
                executor.submit(client);
            } catch (IOException e) {
                //System.err.println("Error while accepting incoming DMAP connection" + e.getMessage());
            }
        }
    }

    public void shutdown() {
        quit = true;

        for (DmapClient client : clients) {
            client.closeConnection();
        }

        executor.shutdown();
    }

    private class DmapClient implements Runnable {

        //private SecretKey secretKey;
        private CryptographyServer cryptographyServer;

        private Socket socket;

        private BufferedReader reader;
        private PrintWriter writer;
        private Config users;

        private boolean isLoggedIn;
        private boolean startSecure;
        private boolean useAES;
        private boolean expectOk;
        private String username;

        public DmapClient(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            initDmap();

            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                writer = new PrintWriter(socket.getOutputStream());

                writer.println("ok DMAP");
                writer.flush();

                String request;

                while ((request = reader.readLine()) != null) {
                    String[] parts = request.split("\\s");

                    if (parts.length != 0) {
                        if (expectOk) {
                            if ((!request.equals("ok"))) {
                                error("protocol error");
                                break;
                            } else {
                                expectOk = false;
                            }
                        } else if (request.equals("startsecure")) {
                            // TODO überprüfung started etc.............
                            startsecure();
                        } else if (parts[0].equals("ok") && parts.length == 4) {
                            challenge(parts[1], parts[2], parts[3]);
                        } else if (parts[0].equals("login") && parts.length == 3) {
                            login(parts[1], parts[2]);
                        } else if (parts[0].equals("list") && parts.length == 1) {
                            list();
                        } else if (parts[0].equals("show") && parts.length == 2 && Validator.isInteger(parts[1])) {
                            show(Integer.parseInt(parts[1]));
                        } else if (parts[0].equals("delete") && parts.length == 2 && Validator.isInteger(parts[1])) {
                            delete(Integer.parseInt(parts[1]));
                        } else if (parts[0].equals("logout") && parts.length == 1) {
                            logout();
                        } else if (parts[0].equals("quit") && parts.length == 1) {
                            quit();
                        } else {
                            error("protocol error");
                            break;
                        }
                    } else {
                        error("protocol error");
                        break;
                    }
                }
            } catch (IOException e) {
                //System.out.println("Error while communication via DMAP");
            } finally {
                closeConnection();
            }
        }

        public void initDmap() {
            isLoggedIn = false;
            startSecure = false;
            useAES = false;
            expectOk = false;
            this.username = null;
            users = new Config("users-" + componentId.substring(8));
            cryptographyServer = new CryptographyServer(componentId);
        }

        public void startsecure() {
            //TODO boolean var ?
            startSecure = true;
            writer.println("ok " + componentId);
            writer.flush();
        }

        public void challenge(String clientChallenge, String secretKey, String iv) {
            // TODO AES
            if (startSecure && !isLoggedIn) {
                byte[] decodedKey = Base64.getDecoder().decode(secretKey);
                SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
                cryptographyServer.setSecretKey(originalKey);

                IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes());
                cryptographyServer.setIv(ivSpec);

                String s = cryptographyServer.decryptRSA(clientChallenge);
                writer.println("ok " + s);
                writer.flush();

                expectOk = true;
                useAES = true;
            }
        }

        public void login(String username, String password) {
            if (!isLoggedIn) {
                if (users.containsKey(username)) {
                    if (users.getString(username).equals(password)) {
                        isLoggedIn = true;
                        this.username = username;
                        ok();
                    } else {
                        error("wrong password");
                    }
                } else {
                    error("unknown user");
                }
            } else {
                error("already logged in as " + this.username);
            }
        }

        public void list() {
            if (isLoggedIn) {
                Iterator<Message> it = mailboxStorage.iteratorForKey(username);
                if (it != null && it.hasNext()) {
                    Message m;
                    while (it.hasNext()) {
                        m = it.next();
                        if (m != null) {
                            writer.println(m.getId() + " " + m.getFrom() + " " + m.getSubject());
                        }
                    }
                    writer.flush();
                } else {
                    ok();
                }
            } else {
                error("not logged in");
            }
        }

        public void show(Integer id) {
            if (isLoggedIn) {
                Message m = mailboxStorage.showMessage(username, id);
                if (m != null) {
                    writer.println("from " + m.getFrom());
                    writer.println("to " + String.join(",", m.getTo()));
                    writer.println("subject " + m.getSubject());
                    writer.println("data " + m.getData());
                    writer.flush();
                } else {
                    error("unknown message id");
                }
            } else {
                error("not logged in");
            }
        }

        public void delete(Integer id) {
            if (isLoggedIn) {
                if (mailboxStorage.deleteMessage(username, id)) {
                    ok();
                } else {
                    error("unknown message id");
                }
            } else {
                error("not logged in");
            }
        }

        public void logout() {
            if (isLoggedIn) {
                isLoggedIn = false;
                username = null;
                ok();
            } else {
                error("was not logged in");
            }
        }

        public void ok() {
            writer.println("ok");
            writer.flush();
        }

        public void error(String message) {
            writer.println("error " + message);
            writer.flush();
        }

        public void quit() {
            writer.println("ok bye");
            writer.flush();
            closeConnection();
        }

        public void closeConnection() {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Cannot handle
                }
            }

            if (writer != null) {
                writer.close();
            }

            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    //Cannot handle
                }
            }
        }

    }

}
