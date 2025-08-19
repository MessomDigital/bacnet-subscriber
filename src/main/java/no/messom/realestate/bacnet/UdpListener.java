package no.messom.realestate.bacnet;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

import static org.slf4j.LoggerFactory.getLogger;

public class UdpListener extends Thread {
    private static final Logger log = getLogger(UdpListener.class);
    private DatagramSocket socket;
    private boolean listening;
    private boolean recording;
    public static final int BACNET_DEFAULT_PORT = 47808;

    private long messageCount = 0;
    File recordingFile = null;

    public UdpListener() throws SocketException {
        socket = new DatagramSocket(null);
        socket.setBroadcast(true);
        socket.setReuseAddress(true);
        SocketAddress inetAddress = new InetSocketAddress(BACNET_DEFAULT_PORT);
        socket.bind(inetAddress);
    }

    public void run() {
        listening = true;

        while (listening) {
            byte[] buf = new byte[2048]; // Midlertidig buffer for å motta data
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                log.error("Error receiving packet", e);
                continue;
            }

            // Opprett en ny buffer basert på faktisk lengde av dataene
            byte[] receivedBytes = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), 0, receivedBytes, 0, packet.getLength());

            InetAddress sourceAddress = packet.getAddress();
            int sourcePort = packet.getPort();
            String received = new String(receivedBytes, StandardCharsets.UTF_8);
            log.info("Received packet from {}:{}: {}.", sourceAddress, sourcePort, received);
            addMessageCount();
        }
        socket.close();
    }

    void addMessageCount() {
        if (messageCount < Long.MAX_VALUE) {
            messageCount++;
        } else {
            messageCount = 1;
        }
    }

    public long getMessageCount() {
        return messageCount;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    public static void main(String[] args) {
        try {
            UdpListener udpServer = new UdpListener();
            udpServer.start();
            log.info("UDP Server started on port {}", BACNET_DEFAULT_PORT);
        } catch (SocketException e) {
            log.error("Failed to start UDP server", e);
        }
    }
}