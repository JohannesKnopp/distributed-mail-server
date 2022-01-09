package dslab.nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import at.ac.tuwien.dsg.orvell.Shell;
import dslab.ComponentFactory;
import dslab.util.Config;

public class Nameserver implements INameserver {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;

    private ServerSocket serverSocket;
    private Shell shell;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        //shell = new Shell(in, out);
        //shell.register(this);
        //shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        // 2 Cases, Root, Zone
        // Root -> Remote Registry
        try {
            INameserverRemote remote = new NameserverRemote(componentId, config);
            if (config.containsKey("domain")) {
                Registry registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
                INameserverRemote registryRemote = (INameserverRemote) registry.lookup(config.getString("root_id"));
                registryRemote.registerNameserver(config.getString("domain"), remote);
            } else {
                Registry registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
                try {
                    registry.bind(config.getString("root_id"), remote);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            //TODO: ALLE EXCEPTIONS
        }

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void nameservers() {
        // TODO
    }

    @Override
    public void addresses() {
        // TODO
    }

    @Override
    public void shutdown() {
        // TODO
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
