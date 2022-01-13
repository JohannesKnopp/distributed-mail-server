package dslab.nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.rmi.AccessException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class Nameserver implements INameserver {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;

    private ServerSocket serverSocket;
    private Shell shell;
    private INameserverRemote remote;
    private ConcurrentHashMap<String, INameserverRemote> children; // TODO concurrent
    private ConcurrentHashMap<String, String> mailboxServers; // TODO concurrent
    private Registry registry;

    private ArrayList<INameserverRemote> registries;
    private ArrayList<Registry> regs;

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
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
        children = new ConcurrentHashMap<String, INameserverRemote>();
        mailboxServers = new ConcurrentHashMap<String, String>();
        registries = new ArrayList<INameserverRemote>();
        regs = new ArrayList<Registry>();
    }

    @Override
    public void run() {
        // 2 Cases, Root, Zone
        // Root -> Remote Registry
        try {
            remote = new NameserverRemote(componentId, config, children, mailboxServers, out);
            if (config.containsKey("domain")) {
                registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
                INameserverRemote registryRemote = (INameserverRemote) registry.lookup(config.getString("root_id"));
                registryRemote.registerNameserver(config.getString("domain"), remote);
                registries.add(registryRemote);
                regs.add(registry);
            } else {
                registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
                regs.add(registry);
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

        shell.run();
    }

    @Override
    @Command
    public void nameservers() {
        int counter = 1;
        for (String nameserver : children.keySet()) {
            out.println(counter + ". " + nameserver);
        }
    }

    @Override
    @Command
    public void addresses() {
        int counter = 1;
        for (String name : mailboxServers.keySet()) {
            out.println(counter + ". " + name + " " + mailboxServers.get(name));
        }

    }

    @Override
    @Command
    public void shutdown() {
//        try {
//            UnicastRemoteObject.unexportObject(remote, true);
//            if (!config.containsKey("domain")) {
//                registry.unbind(config.getString("root_id"));
//            }
//        } catch (NoSuchObjectException e) {
//            e.printStackTrace();
//        } catch (AccessException e) {
//            e.printStackTrace();
//        } catch (RemoteException e) {
//            e.printStackTrace();
//        } catch (NotBoundException e) {
//            e.printStackTrace();
//        }

        try {
            UnicastRemoteObject.unexportObject(remote, true);
            if (!config.containsKey("domain")) {

                for (INameserverRemote reg : registries) {
                    UnicastRemoteObject.unexportObject(reg, true);
                }
                for (Registry reg: regs) {
                    UnicastRemoteObject.unexportObject(reg, true);
                }
                registry.unbind(config.getString("root_id"));
            }
        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        } catch (AccessException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }

        shell.out().println("Shutting down...");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
