package dslab.nameserver;

import dslab.util.Config;

import java.io.PrintStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NameserverRemote extends UnicastRemoteObject implements INameserverRemote {

    private Registry registry;
    private Config config;
    private int registryPort;
    private String registryHost;
    private String rootId;
    private String domain;
    private PrintStream out;

    private ConcurrentHashMap<String, INameserverRemote> children;
    private ConcurrentHashMap<String, String> mailboxServers;

    public NameserverRemote(String componentId, Config config, ConcurrentHashMap<String, INameserverRemote> children, ConcurrentHashMap<String, String> mailboxServers, PrintStream out) throws RemoteException {
        super();
        registryPort = config.getInt("registry.port");
        registryHost = config.getString("registry.host");
        rootId = config.getString("root_id");
        domain = null;
        if (config.containsKey("domain")) {
            domain = config.getString("domain");
        }

        this.children = children;
        this.mailboxServers = mailboxServers;
        this.out = out;
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, InvalidDomainException {
        try {
            if (domain.contains(".")) {
                out.println(LocalDateTime.now().toString() + " : Registering Nameserver for zone '" + domain.substring(0, domain.indexOf('.')) + "'");
                String lastDomain = domain.substring(domain.lastIndexOf(".")+1);
                if (children.containsKey(lastDomain)) {
                    INameserverRemote childRemote = children.get(lastDomain);
                    String subDomain = domain.substring(0, domain.lastIndexOf('.'));
                    childRemote.registerNameserver(subDomain, nameserver);
                } else {
                    out.println(LocalDateTime.now().toString() + " : could not register zone"
                            + " '" + domain.substring(0, domain.indexOf('.')) + "' for nameserver because nameserver " + lastDomain + " not found");
                    throw new InvalidDomainException("");
                }
            } else {
                if (!children.containsKey(domain)) {
                    out.println(LocalDateTime.now().toString() + " : Registering Nameserver for zone '" + domain + "'");
                    children.put(domain, nameserver);
                } else {
                    out.println(LocalDateTime.now().toString() + " : Nameserver for zone '" + domain + "' already registered!");
                    throw new AlreadyRegisteredException("");
                }
            }
        } catch (AlreadyRegisteredException e) {
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        try {
            if (domain.contains(".")) {
                out.println(LocalDateTime.now().toString() + " : Registering mailbox server '" + domain.substring(0, domain.indexOf('.')) + "'");
                String lastDomain = domain.substring(domain.lastIndexOf(".")+1);
                if (children.containsKey(lastDomain)) {
                    INameserverRemote childRemote = children.get(lastDomain);
                    String subDomain = domain.substring(0, domain.lastIndexOf('.'));
                    childRemote.registerMailboxServer(subDomain, address);
                } else {
                    out.println(LocalDateTime.now().toString() + " : could not register mailbox server"
                            + " '" + domain.substring(0, domain.indexOf('.')) + "' because nameserver " + lastDomain + " not found");
                    throw new InvalidDomainException("");
                }
            } else {
                if (mailboxServers.containsKey(domain)) {
                    out.println(LocalDateTime.now().toString() + " : mailbox server '" + domain + "' already registered!");
                    throw new AlreadyRegisteredException("");
                } else {
                    out.println(LocalDateTime.now().toString() + " : Registering mailbox server for zone '" + domain + "'");
                    mailboxServers.put(domain, address);
                }
            }
        } catch (AlreadyRegisteredException e) {
            // nothing to do
        }
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        out.println(LocalDateTime.now().toString() + " : Requested Nameserver for zone '" + zone + "' by transfer server");
        if (children.containsKey(zone)) {
            return children.get(zone);
        } else {
            throw new RemoteException("");
        }
    }

    @Override
    public String lookup(String username) throws RemoteException {
        out.println(LocalDateTime.now().toString() + " : Requested mailbox server '" + username + "' by transfer server");
        return mailboxServers.get(username);
    }
}
