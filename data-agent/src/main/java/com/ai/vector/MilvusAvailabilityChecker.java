package com.ai.vector;

import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Checks whether Milvus is reachable before initializing the vector store.
 *
 * @author data-agent
 */
@Component
public class MilvusAvailabilityChecker {

    private static final int SOCKET_CONNECT_TIMEOUT_MS = 3000;

    /**
     * Tests TCP connectivity to the configured Milvus endpoint.
     *
     * @param host Milvus host
     * @param port Milvus port
     * @return true when the endpoint accepts a socket connection
     */
    public boolean isAvailable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
