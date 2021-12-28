package dslab.mailbox;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.dmap.DmapServerThread;
import dslab.dmtp.DmtpServerThread;
import dslab.entity.MailboxStorage;
import dslab.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public class MailboxServer implements IMailboxServer, Runnable {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private Shell shell;

    private ServerSocket dmtpServerSocket;
    private ServerSocket dmapServerSocket;

    private DmtpServerThread dmtpServerThread;
    private DmapServerThread dmapServerThread;

    private MailboxStorage mailboxStorage;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
        mailboxStorage = new MailboxStorage();
    }

    @Override
    public void run() {
        String domain = componentId.substring(8).replace('-', '.');
        Map<String, String> domains = new HashMap<>();
        domains.put(domain, "");
        try {
            dmtpServerSocket = new ServerSocket(config.getInt("dmtp.tcp.port"));
            dmtpServerThread = new DmtpServerThread(dmtpServerSocket, componentId, mailboxStorage, domains);
            dmtpServerThread.start();

            dmapServerSocket = new ServerSocket(config.getInt("dmap.tcp.port"));
            dmapServerThread = new DmapServerThread(dmapServerSocket, componentId, mailboxStorage, config);
            dmapServerThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }

        shell.run();
    }

    @Override
    @Command
    public void shutdown() {
        if (dmtpServerThread != null) {
            dmtpServerThread.shutdown();
        }

        if (dmtpServerSocket != null && !dmtpServerSocket.isClosed()) {
            try {
                dmtpServerSocket.close();
            } catch (IOException e) {
                System.err.println("Error while closing DMTP server socket: " + e.getMessage());
            }
        }

        if (dmapServerThread != null) {
            dmapServerThread.shutdown();
        }

        if (dmapServerSocket != null && !dmapServerSocket.isClosed()) {
            try {
                dmapServerSocket.close();
            } catch (IOException e) {
                System.err.println("Error while closing DMAP server socket: " + e.getMessage());
            }
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
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
