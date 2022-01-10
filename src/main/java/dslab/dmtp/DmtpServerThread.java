package dslab.dmtp;

import dslab.entity.MailboxStorage;
import dslab.entity.Message;
import dslab.exception.DmtpException;
import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.transfer.TransferDeliveryHandler;
import dslab.util.Config;
import dslab.util.Helper;
import dslab.util.Validator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class DmtpServerThread extends Thread {

    public enum ServerType {
        MAILBOX_SERVER,
        TRANSFER_SERVER
    }

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private String componentId;
    private Map<String, String> acceptedDomains;
    private MailboxStorage mailboxStorage;
    private Config config;
    private ConcurrentLinkedQueue<DmtpClient> clients;
    private TransferDeliveryHandler transferDeliveryHandler;

    private ServerType serverType;

    private boolean quit = false;

    public DmtpServerThread(ServerSocket serverSocket, String componentId, MailboxStorage mailboxStorage,
                            Map<String, String> acceptedDomains) {
        this.serverSocket = serverSocket;
        this.componentId = componentId;
        this.mailboxStorage = mailboxStorage;
        this.acceptedDomains = acceptedDomains;
    }

    public void run() {
        this.clients = new ConcurrentLinkedQueue<>();
        init();

        executor = Executors.newCachedThreadPool();
        while (!quit) {
            try {
                DmtpClient client = new DmtpClient(serverSocket.accept());
                clients.add(client);
                executor.submit(client);
            } catch (IOException e) {
                //System.err.println("Error while accepting incoming DMTP connection!" + e.getMessage());
            }
        }
    }

    public void shutdown() {
        quit = true;

        if (serverType == ServerType.TRANSFER_SERVER) {
            transferDeliveryHandler.shutdown();
        }

        for (DmtpClient client : clients) {
            client.closeConnection();
        }

        executor.shutdown();
    }

    private void writeMessageMailbox(String recipient, String from, ArrayList<String> to, String subject, String data, String hash) {
        Message message = new Message(null, from, to, subject, data, hash);
        mailboxStorage.writeMessage(recipient, message);
    }

    public void init() {

        config = new Config(componentId);
        if (componentId.startsWith("transfer")) {
            serverType = ServerType.TRANSFER_SERVER;
            transferDeliveryHandler = new TransferDeliveryHandler(componentId, acceptedDomains, config);
            transferDeliveryHandler.start();
        } else if (componentId.startsWith("mailbox")) {
            serverType = ServerType.MAILBOX_SERVER;
            Registry registry = null;
            try {
                registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
                INameserverRemote registryRemote = (INameserverRemote) registry.lookup(config.getString("root_id"));
                registryRemote.registerMailboxServer(config.getString("domain"), "localhost:" + config.getString("dmtp.tcp.port"));
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            } catch (InvalidDomainException e) {
                e.printStackTrace();
            } catch (AlreadyRegisteredException e) {
                e.printStackTrace();
            }
        }
    }

    private class DmtpClient implements Runnable {

        private Socket socket;

        private boolean dmtpBegin;
        private ArrayList<String> dmtpTo;
        private String dmtpFrom;
        private String dmtpSubject;
        private String dmtpData;
        private String dmtpHash;

        private BufferedReader reader;
        private PrintWriter writer;
        private Config users;

        DmtpClient(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            initDmtp();

            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                writer = new PrintWriter(socket.getOutputStream());

                writer.println("ok DMTP2.0");
                writer.flush();

                String request;

                while ((request = reader.readLine()) != null) {
                    String[] parts = request.split("\\s");

                    if (parts.length != 0) {
                        if (request.equals("begin")) {
                            begin();
                        } else if (parts[0].equals("to") && parts.length == 2) {
                            to(parts[1]);
                        } else if (parts[0].equals("from") && parts.length == 2) {
                            from(parts[1]);
                        } else if (parts[0].equals("subject") && parts.length >= 2) {
                            subject(request.substring(8));
                        } else if (parts[0].equals("data") && parts.length >= 2) {
                            data(request.substring(5));
                        } else if (parts[0].equals("hash") && parts.length == 2) {
                            hash(parts[1]);
                        } else if (request.equals("send")) {
                            send();
                        } else if (request.equals("quit")) {
                            quit();
                            break;
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
                //System.out.println("Error while communicating via DMTP: " +  e.getMessage());
            } finally {
                closeConnection();
            }
        }

        public void initDmtp() {
            dmtpBegin = false;
            dmtpTo = new ArrayList<>();
            dmtpFrom = null;
            dmtpSubject = null;
            dmtpData = null;
            dmtpHash = null;
            users = null;

            if (serverType == ServerType.MAILBOX_SERVER) {
                users = new Config(config.getString("users.config"));
            }
        }

        public void begin() {
            initDmtp();
            dmtpBegin = true;
            ok();
        }

        public void to(String recipients) {
            dmtpTo.clear();
            if (dmtpBegin) {
                boolean valid = true;
                int validCounter = 0;
                String unknownRecipient = "";

                for (String s : recipients.split(",")) {
                    unknownRecipient = s.split("@")[0];
                    if (!Validator.isValidEmail(s)) {
                        valid = false;
                        break;
                    } else if (serverType == ServerType.MAILBOX_SERVER) {

                        if (Validator.isValidDomain(s, acceptedDomains)) {
                            if (!users.containsKey(unknownRecipient)) {
                                valid = false;
                                break;
                            } else {
                                if (!dmtpTo.contains(s)) {
                                    dmtpTo.add(s);
                                    validCounter++;
                                } else {
                                    error("same recipient multiple times");
                                    return;
                                }
                            }
                        }
                    } else if (serverType == ServerType.TRANSFER_SERVER) {
                        if (!dmtpTo.contains(s)) {
                            dmtpTo.add(s);
                            validCounter++;
                        } else {
                            error("same recipient multiple times");
                            return;
                        }
                    }
                }

                if (!valid) {
                    error("unknown recipient " + unknownRecipient);
                    dmtpTo.clear();
                } else if (serverType == ServerType.MAILBOX_SERVER && validCounter == 0) {
                    error("invalid domains");
                    dmtpTo.clear();
                } else {
                    writer.println("ok " + validCounter);
                    writer.flush();
                }
            } else {
                error("type begin to start new message");
            }
        }

        public void from(String sender) {
            if (dmtpBegin) {
                String un = sender.split("@")[0];
                if (!Validator.isValidEmail(sender)) {
                    error("invalid sender " + sender);
                } else {
                    dmtpFrom = sender;
                    ok();
                }
            } else {
                error("type begin to start new message");
            }
        }

        public void subject(String subject) {
            if (dmtpBegin) {
                dmtpSubject = subject;
                ok();
            } else {
                error("type begin to start new message");
            }
        }

        public void data(String data) {
            if (dmtpBegin) {
                dmtpData = data;
                ok();
            } else {
                error("type begin to start new message");
            }
        }

        public void hash(String hash) {
            if (dmtpBegin) {
                dmtpHash = hash;
                ok();
            } else {
                error("type begin to start new message");
            }
        }

        public void send() {
            if (dmtpBegin) {
                if (dmtpTo.isEmpty()) {
                    error("no recipient");
                } else if (dmtpFrom == null) {
                    error("no sender");
                } else if (dmtpSubject == null) {
                    error("no subject");
                } else if (dmtpData == null) {
                    error("no data");
                } else if (serverType == ServerType.MAILBOX_SERVER) {
                    for (String recipient : dmtpTo) {
                        writeMessageMailbox(recipient, dmtpFrom, dmtpTo, dmtpSubject, dmtpData, dmtpHash);
                    }
                    ok();
                } else if (serverType == ServerType.TRANSFER_SERVER) {
                    ok();
                    writeMessageTransfer(dmtpFrom, dmtpTo, dmtpSubject, dmtpData, dmtpHash);
                }
            } else {
                error("type begin to start new message");
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

        private void writeMessageTransfer(String from, ArrayList<String> to, String subject, String data, String hash) {
            transferDeliveryHandler.addTask(new Message(null, from, to, subject, data, hash));
        }
    }



}



































