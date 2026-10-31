package com.ai.background;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** 仅续租本机正在执行的任务，统一处理失去租约后的中断和监听线程复用。 */
@Component
public class TaskLeases {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskLeases.class);
    private final Map<String, Lease> active = new ConcurrentHashMap<>();
    private volatile boolean closing;

    /** 在 Rabbit 监听线程中登记已由数据库 CAS 领取的租约。 */
    public Lease track(BooleanSupplier renew) {
        if (closing) throw new IllegalStateException("后台消费者正在停止");
        Lease lease = new Lease(UUID.randomUUID().toString(), renew);
        active.put(lease.key, lease);
        if (closing) {
            lease.close();
            throw new IllegalStateException("后台消费者正在停止");
        }
        return lease;
    }

    /** 空闲时没有数据库调用，只为正在执行的任务续期。 */
    @Scheduled(fixedDelay = 10000)
    public void renew() {
        active.values().forEach(lease -> {
            synchronized (lease) {
                if (lease.thread == null) return;
                try {
                    if (lease.renew.getAsBoolean()) return;
                } catch (RuntimeException error) {
                    LOGGER.warn("后台任务续租失败: errorType={}", error.getClass().getSimpleName());
                }
                lease.owned = false;
                lease.thread.interrupt();
            }
        });
    }

    /** 不清空数据库任务，未完成工作由下一进程在租约过期后恢复。 */
    @PreDestroy
    public void stop() {
        closing = true;
        active.values().forEach(lease -> {
            synchronized (lease) {
                lease.owned = false;
                if (lease.thread != null) lease.thread.interrupt();
            }
        });
    }

    /** 本机租约句柄不代表外部存储支持 fencing。 */
    public final class Lease implements AutoCloseable {
        private final String key;
        private final BooleanSupplier renew;
        private volatile Thread thread = Thread.currentThread();
        private volatile boolean owned = true;
        private Lease(String key, BooleanSupplier renew) { this.key = key; this.renew = renew; }
        public void ensureOwned() {
            if (!owned || Thread.currentThread().isInterrupted()) throw new IllegalStateException("后台任务执行权已失效");
        }
        @Override public void close() {
            synchronized (this) {
                thread = null;
                active.remove(key);
                Thread.interrupted();
            }
        }
    }
}
