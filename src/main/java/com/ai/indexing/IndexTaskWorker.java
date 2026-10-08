package com.ai.indexing;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 扫描只认领任务；外部 IO 交给独立有界池。租约和版本 CAS 保护数据库任务状态。 */
@Component
public class IndexTaskWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexTaskWorker.class);
    private final IndexTaskStore store;
    private final IndexTaskProcessor processor;
    private final Executor executor;
    private final Semaphore slots = new Semaphore(2);
    private final Map<String, Active> active = new ConcurrentHashMap<>();
    private final AtomicBoolean closing = new AtomicBoolean();

    public IndexTaskWorker(IndexTaskStore store, IndexTaskProcessor processor,
            @Qualifier("indexTaskExecutor") Executor executor) {
        this.store = store;
        this.processor = processor;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${app.index-tasks.scan-delay-ms:2000}")
    public void scan() {
        if (closing.get()) return;
        for (IndexTask candidate : store.ready(10)) {
            if (closing.get() || !slots.tryAcquire()) break;
            IndexTask task;
            try {
                task = store.claim(candidate);
            } catch (RuntimeException error) {
                slots.release();
                throw error;
            }
            if (task == null) {
                slots.release();
                continue;
            }
            Active running = new Active(task);
            active.put(task.owner(), running);
            try {
                executor.execute(() -> execute(running));
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                active.remove(task.owner());
                slots.release();
                store.release(task);
            }
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void renew() {
        if (closing.get()) return;
        active.values().forEach(running -> {
            synchronized (running) {
                if (running.thread == null) return;
                try {
                    if (store.renew(running.task)) return;
                } catch (RuntimeException error) {
                    LOGGER.warn("索引任务续租失败: taskId={}", running.task.id());
                }
                running.owned.set(false);
                running.thread.interrupt();
            }
        });
    }

    @Scheduled(fixedDelay = 3600000)
    public void purge() {
        store.purgeCompletedConversations();
    }

    /** 停机不清空持久任务，未完成任务由下次扫描在租约过期后接管。 */
    @PreDestroy
    public void close() {
        closing.set(true);
        active.values().forEach(running -> {
            synchronized (running) {
                running.owned.set(false);
                if (running.thread != null) running.thread.interrupt();
            }
        });
    }

    private void execute(Active running) {
        running.thread = Thread.currentThread();
        Throwable failure = null;
        try {
            if (closing.get()) throw new IllegalStateException("索引工作线程正在停止");
            running.ensureOwned();
            IndexTask task = running.task;
            processor.process(task);
            running.ensureOwned();
        } catch (Throwable error) {
            failure = error;
            LOGGER.warn("索引任务失败: taskId={}, version={}, errorType={}",
                    running.task.id(), running.task.version(), error.getClass().getSimpleName());
            if (error instanceof Error fatal) throw fatal;
        } finally {
            // 先停止续租线程向本线程发送中断，再提交重放状态，避免中断泄漏给下一项任务。
            synchronized (running) {
                running.thread = null;
                Thread.interrupted();
            }
            try {
                store.finish(running.task, failure);
            } finally {
                active.remove(running.task.owner());
                slots.release();
            }
        }
    }

    private static final class Active {
        private final IndexTask task;
        private final AtomicBoolean owned = new AtomicBoolean(true);
        private volatile Thread thread;

        private Active(IndexTask task) {
            this.task = task;
        }

        private void ensureOwned() {
            if (!owned.get() || Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("索引执行权已失效");
            }
        }
    }
}
