package com.ai.vector;

import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 在初始化向量存储之前检查 Milvus 是否可达。
 *
 * @author data-agent
 */
@Component
public class MilvusAvailabilityChecker {

    private static final int SOCKET_CONNECT_TIMEOUT_MS = 3000;

    /**
     * 测试到配置的 Milvus 端点的 TCP 连接。
     *
     * @param host Milvus 主机
     * @param port Milvus 端口
     * @return 端点接受套接字连接时返回 true
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
