package dslab.transfer;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.dmtp.DmtpServerThread;
import dslab.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public class TransferServer implements ITransferServer, Runnable {

    private Config config;
    private InputStream in;
    private PrintStream out;
    private Shell shell;
    private String componentId;

    private Map<String, String> domains = new HashMap<>();

    private ServerSocket dmtpServerSocket;
    private DmtpServerThread dmtpServerThread;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
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
        domainLookup();
        try {
            dmtpServerSocket = new ServerSocket(config.getInt("tcp.port"));
            dmtpServerThread = new DmtpServerThread(dmtpServerSocket, componentId, null, domains);
            dmtpServerThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }

        shell.run();
    }

    @Override
    @Command
    public void shutdown() {
        if (dmtpServerSocket != null && !dmtpServerSocket.isClosed()) {
            try {
                dmtpServerSocket.close();
            } catch (IOException e) {
                System.err.println("Error while closing DMTP server socket: " + e.getMessage());
            }
        }

        if (dmtpServerThread != null) {
            dmtpServerThread.shutdown();
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

    public void domainLookup() {
//        Config domains = new Config("domains");
//        for (String s : domains.listKeys()) {
//            this.domains.put(s, domains.getString(s));
//        }
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
