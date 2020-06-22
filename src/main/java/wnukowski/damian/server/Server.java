package wnukowski.damian.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;


public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private static final int PORT = Integer.parseInt(System.getProperty("serverPort", "44322"));
    private static final String jksFilePath = System.getProperty("jksFilePath", "ssl/certificate.jks");
    private static final String jksPassPhrase = System.getProperty("jksPassPhrase", "passphrase");
    private static final boolean shouldRun = true;

    public static void start() {
        log.info("Starting server on port: [{}]",  PORT);
        try (ServerSocket serverSocket = getSslServerSocket(PORT, jksFilePath, jksPassPhrase)) {
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

    private static ServerSocket getSslServerSocket(int port, String jksLocation, String jksPassPhrase) throws Exception {
        try {
            SSLServerSocketFactory ssf;
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            char[] passphrase = jksPassPhrase.toCharArray();

            ctx = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS");

            ks.load(new FileInputStream(jksLocation), passphrase);
            kmf.init(ks, passphrase);
            ctx.init(kmf.getKeyManagers(), null, null);

            ssf = ctx.getServerSocketFactory();
            return ssf.createServerSocket(port);
        } catch (Exception e) {
            throw new Exception("Can't create ssl server socket, application stops", e);
        }
    }
}
