package dslab.util;

import dslab.exception.DmtpException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

public class Helper {

    public static Socket waitForSocket(String host, int port, int ms) throws SocketTimeoutException {
        SocketAddress addr = new InetSocketAddress(host, port);
        Socket socket = new Socket();

        try {
            socket.connect(addr, ms);
        } catch (IOException e) {
            throw new SocketTimeoutException("Gave up waiting for socket " + host + ":" + port);
        }

        return socket;
    }

    public static void readAndVerify(BufferedReader reader, String message) throws IOException {
        String s = reader.readLine();
        if (!s.equals(message)) {
            throw new DmtpException(s);
        }
    }

    public static void writeAndVerify(BufferedReader reader, PrintWriter writer, String request, String response) throws IOException {
        writer.println(request);
        writer.flush();
        readAndVerify(reader, response);
    }

}
