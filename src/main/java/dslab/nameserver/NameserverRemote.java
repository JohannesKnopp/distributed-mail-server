package dslab.nameserver;

import dslab.util.Config;

import java.io.Serializable;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

public class NameserverRemote extends UnicastRemoteObject implements INameserverRemote {

    private Registry registry;
    private Config config;
    private int registryPort;
    private String registryHost;
    private String rootId;
    private String domain;

    private Map<String, INameserverRemote> children;

    public NameserverRemote(String componentId, Config config) throws RemoteException {
        super();
        registryPort = config.getInt("registry.port");
        registryHost = config.getString("registry.host");
        rootId = config.getString("root_id");
        domain = null;
        if (config.containsKey("domain")) {
            domain = config.getString("domain");
        }

        children = new HashMap<String, INameserverRemote>();

        // registerNameserver(domain, remote); // TODO wtf?
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, InvalidDomainException {
        // earth.planet
        // planet

        try {
            if (domain.contains(".")) {
                String lastDomain = domain.substring(domain.lastIndexOf(".")+1);
                System.out.println(lastDomain);
                if (children.containsKey(lastDomain)) {
                    INameserverRemote childRemote = children.get(lastDomain);
                    String subDomain = domain.substring(0, domain.lastIndexOf('.'));
                    childRemote.registerNameserver(subDomain, nameserver);
                } else {
                    throw new InvalidDomainException("");
                }
            } else {
                if (!children.containsKey(domain)) {
                    children.put(domain, nameserver);
                }
            }
        } catch (AlreadyRegisteredException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        if (zone.contains(".")) {
            String z = zone.substring(zone.lastIndexOf(".") + 1);
            if (children.containsKey(z)) {
                INameserverRemote child = children.get(z);
                String sub = zone.substring(0, zone.lastIndexOf('.'));
                return child.getNameserver(sub);
            }
        } else if (children.containsKey(zone)) {
            return children.get(zone);
        }
        throw new RemoteException();
    }

    @Override
    public String lookup(String username) throws RemoteException {
        return null;
    }

}
