package wnukowski.damian.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;
import java.net.Socket;


public class Server {
    public static final int PORT = Integer.parseInt(System.getProperty("serverPort", "44322"));
    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private static final boolean shouldRun = true;

    public static void start() {
        log.info("Starting server on port: [{}]",  PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (shouldRun) {
                String socketAddress = null;
                Integer socketPort = null;
                Socket socket = serverSocket.accept();
                socketAddress = socket.getInetAddress().getHostAddress();
                socketPort = socket.getPort();
                log.info("Connected with socket {}:{}", socketAddress, socketPort);
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (Exception e) {
            log.error("Unexpected error occurred", e);
        }
    }
}
