package dslab.monitoring;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public class MonitoringServer implements IMonitoringServer {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;

    private DatagramSocket datagramSocket;
    private MonitoringServerThread monitoringServerThread;

    private HashMap<String, Integer> addresses;
    private HashMap<String, Integer> servers;

    private Shell shell;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        addresses = new HashMap<>();
        servers = new HashMap<>();
        try {
            datagramSocket = new DatagramSocket(config.getInt("udp.port"));
            monitoringServerThread = new MonitoringServerThread(datagramSocket, addresses, servers);
            monitoringServerThread.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot listen on UDP port", e);
        }

        shell.run();
    }

    @Override
    @Command
    public void addresses() {
        for (String key : addresses.keySet()) {
            out.println(key + " " + addresses.get(key));
        }
    }

    @Override
    @Command
    public void servers() {
        for (String key : servers.keySet()) {
            out.println(key + " " + servers.get(key));
        }
    }

    @Override
    @Command
    public void shutdown() {
        if (datagramSocket != null) {
            datagramSocket.close();
        }

        if (monitoringServerThread != null) {
            monitoringServerThread.shutdown();
        }

        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // Cannot handle
            }
        }

        if (out != null) {
            out.close();
        }

        shell.out().println("Shutting down...");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
