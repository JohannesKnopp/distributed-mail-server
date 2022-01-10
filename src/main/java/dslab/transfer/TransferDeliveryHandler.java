package dslab.transfer;

import dslab.entity.Message;
import dslab.exception.DmtpException;
import dslab.nameserver.INameserverRemote;
import dslab.util.Config;
import dslab.util.Helper;

import java.io.*;
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

public class TransferDeliveryHandler extends Thread {

   // TODO Add queue of mails | add fixed Thread pool | work through queue

    private LinkedBlockingQueue<Message> queue;
    private ExecutorService executor;
    private boolean quit = false;
    private String componentId;
    private Map<String, String> acceptedDomains;
    private Config config;
    private Registry registry;

    private String hostAddress;
    private int hostPort;
    private InetAddress monitoringHost;
    private int monitoringPort;
    private Message POISON_PILL = new Message();

    public TransferDeliveryHandler(String componentId, Map<String, String> acceptedDomains, Config config) {
        this.componentId = componentId;
        this.acceptedDomains = acceptedDomains;
        this.config = config;
    }

    public void run() {
        try {
            registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
        } catch (RemoteException e) {
            e.printStackTrace(); // TODO xd
        }


        this.queue = new LinkedBlockingQueue<>();
        this.executor = Executors.newFixedThreadPool(32);

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
            hostPort = config.getInt("tcp.port");
            monitoringHost = InetAddress.getByName(config.getString("monitoring.host"));
            monitoringPort = config.getInt("monitoring.port");
        } catch (UnknownHostException e) {
            throw new UncheckedIOException(e);
        }

        while (!quit) {
            try {
                Message message = queue.take();
                if (message != POISON_PILL) {
                    MonitoringReporter m = new MonitoringReporter(message, registry);
                    executor.submit(m);
                }
            } catch (InterruptedException | RejectedExecutionException e) {
                //e.printStackTrace();
            }
        }
    }

    public void addTask(Message m) {
        queue.add(m);
    }

    public void shutdown() {
        quit = true;

        queue.add(POISON_PILL);

        executor.shutdown();
    }

    private class MonitoringReporter implements Runnable {

        private Message message;
        private DatagramSocket udpSocket;
        private Registry registry;

        public MonitoringReporter(Message message, Registry registry) {
            this.message = message;
            this.registry = registry;
        }

        public void run() {
            // to u@planet.eav s@er.zeth
            //to -> {planet.eav, er.zeth}

            // earth.planet -> { paul@earth.planet, pepter/ ._ }
            // univer.ze -> { pauliiii@univer.ze }



            Map<String, List<String>> groupByDomain = message.getTo().stream()
                    .collect(Collectors.groupingBy(s -> s.split("@")[1]));

            try {
                udpSocket = new DatagramSocket();

                byte[] buffer;
                DatagramPacket packet;
                String failureAddress = acceptedDomains.get(message.getFrom().split("@")[1]);

                for (String s : groupByDomain.keySet()) {
                    buffer = (hostAddress + ":" + hostPort + " " + message.getFrom()).getBytes();
                    packet = new DatagramPacket(buffer, buffer.length, monitoringHost, monitoringPort);

                    udpSocket.send(packet);

                    String domain = s;
                    INameserverRemote nextNameServer = (INameserverRemote) registry.lookup(config.getString("root_id"));
                    while (domain.contains(".")) {
                        String nextZone = domain.substring(domain.lastIndexOf('.') + 1);
                        nextNameServer = nextNameServer.getNameserver(nextZone);
                        domain = domain.substring(0, domain.lastIndexOf('.'));
                    }

                    String address = nextNameServer.lookup(domain);
                    //String address = acceptedDomains.get(s);

                    TransferThread t = new TransferThread(message, address, failureAddress, s, groupByDomain.get(s).size());
                    executor.submit(t);
                }

            } catch (IOException e) {
                // Cannot handle
            } catch(NotBoundException e){
                e.printStackTrace();

            }
            finally {
                if (udpSocket != null && !udpSocket.isClosed()) {
                    udpSocket.close();
                }
            }
        }
    }

    private class TransferThread implements Runnable {

        private final String address;
        private final String failureAddress;
        private final String domain;

        private final String from;
        private final ArrayList<String> to;
        private final String subject;
        private final String data;
        private final String hash;
        private final int size;

        public TransferThread(Message message, String address, String failureAddress, String domain, int size) {
            this.from = message.getFrom();
            this.to = message.getTo();
            this.subject = message.getSubject();
            this.data = message.getData();
            this.hash = message.getHash();
            this.address = address;
            this.failureAddress = failureAddress;
            this.domain = domain;
            this.size = size;
        }

        @Override
        public void run() {
            if (address == null) {
                deliveryFailure("error could not find domain", from);
                return;
            }

            String[] temp = address.split(":");
            String hostname = temp[0];
            int port = Integer.parseInt(temp[1]);

            BufferedReader reader = null;
            PrintWriter writer = null;

            Socket conn = null;
            try {
                conn = Helper.waitForSocket(hostname, port, 5000);

                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                writer = new PrintWriter(conn.getOutputStream());

                Helper.readAndVerify(reader, "ok DMTP2.0");

                Helper.writeAndVerify(reader, writer, "begin", "ok");

                Helper.writeAndVerify(reader, writer, "from " + from, "ok");

                Helper.writeAndVerify(reader, writer, "data " + data, "ok");

                Helper.writeAndVerify(reader, writer, "subject " + subject, "ok");

                Helper.writeAndVerify(reader, writer, "to " + String.join(",", to), "ok " + size);

                if (hash != null) {
                    Helper.writeAndVerify(reader, writer, "hash " + hash, "ok");
                }

                Helper.writeAndVerify(reader, writer, "send", "ok");

                Helper.writeAndVerify(reader, writer, "quit", "ok bye");
            } catch (SocketTimeoutException e) {
                deliveryFailure("error timeout - " + domain, from);
            } catch (DmtpException e) {
                deliveryFailure(e.getMessage() + " - " + domain, from);
            } catch (IOException e) {
                deliveryFailure("error IO-Error - " + domain, from);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

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
            }
        }

        public void deliveryFailure(String reason, String recipient) {
            if (failureAddress == null) {
                return;
            }

            String[] temp = failureAddress.split(":");
            String hostname = temp[0];
            int port = Integer.parseInt(temp[1]);

            BufferedReader reader = null;
            PrintWriter writer = null;

            Socket conn = null;
            try {
                conn = Helper.waitForSocket(hostname, port, 5000);

                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                writer = new PrintWriter(conn.getOutputStream());

                Helper.readAndVerify(reader, "ok DMTP2.0");

                Helper.writeAndVerify(reader, writer, "begin", "ok");

                // TODO add componentId
                Helper.writeAndVerify(reader, writer, "from no-reply@" + componentId + ".edu", "ok");

                Helper.writeAndVerify(reader, writer, "to " + recipient, "ok 1");

                Helper.writeAndVerify(reader, writer, "subject error delivery failure", "ok");

                Helper.writeAndVerify(reader, writer, "data " + reason, "ok");

                Helper.writeAndVerify(reader, writer, "send", "ok");

                Helper.writeAndVerify(reader, writer, "quit", "ok bye");
            } catch (IOException e) {
                // delivery failure, nothing more to do
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (IOException e) {
                        // Cannot handle
                    }
                }

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
            }
        }
    }

}











