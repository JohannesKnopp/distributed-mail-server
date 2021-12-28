package dslab.monitoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public class MonitoringServerThread extends Thread {

    private DatagramSocket datagramSocket;

    private HashMap<String, Integer> addresses;
    private HashMap<String, Integer> servers;

    private boolean quit = false;

    public MonitoringServerThread(DatagramSocket datagramSocket, HashMap<String, Integer> addresses,
                                    HashMap<String, Integer> servers) {
        this.datagramSocket = datagramSocket;
        this.addresses = addresses;
        this.servers = servers;
    }

    public void run() {
        byte[] buffer;
        DatagramPacket packet;

        try {
            while (!quit) {
                buffer = new byte[1024];

                packet = new DatagramPacket(buffer, buffer.length);

                datagramSocket.receive(packet);

                String request = new String(packet.getData()).trim();

                handleRequest(request);
            }
        } catch (SocketException e) {
            // receive failed, continue
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (datagramSocket != null && !datagramSocket.isClosed()) {
                datagramSocket.close();
            }
        }
    }

    public void handleRequest(String request) {
        String[] parts = request.split("\\s");

        if (parts.length != 2) return;

        int val = addresses.containsKey(parts[1]) ? addresses.get(parts[1]) + 1 : 1;

        addresses.put(parts[1], val);

        val = servers.containsKey(parts[0]) ? servers.get(parts[0]) + 1 : 1;

        servers.put(parts[0], val);
    }

    public void shutdown() {
        quit = true;
    }

}
