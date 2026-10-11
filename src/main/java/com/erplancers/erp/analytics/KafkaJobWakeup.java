package com.erplancers.erp.analytics;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka is a low-latency wake-up channel; PostgreSQL remains the durable queue and
 * polling fallback. Broker downtime or duplicate delivery therefore cannot lose a job.
 */
public final class KafkaJobWakeup implements AutoCloseable {
    private static final String DEFAULT_TOPIC = "erp.analytics.job.queued.v1";
    private final Semaphore signals = new Semaphore(0);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread consumerThread;
    private volatile KafkaConsumer<String, String> activeConsumer;

    private KafkaJobWakeup(String bootstrapServers, String topic) {
        consumerThread = new Thread(() -> consume(bootstrapServers, topic), "analytics-kafka-wakeup");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }
    public static KafkaJobWakeup fromEnvironment() {
        String bootstrap = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrap == null || bootstrap.isBlank()) return new KafkaJobWakeup();
        return new KafkaJobWakeup(bootstrap,
                System.getenv().getOrDefault("KAFKA_ANALYTICS_JOB_TOPIC", DEFAULT_TOPIC));
    }
    private KafkaJobWakeup() { consumerThread = null; }

    private void consume(String bootstrap, String topic) {
        while (running.get()) {
            Properties p = new Properties();
            p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            p.put(ConsumerConfig.GROUP_ID_CONFIG, "erplancers-analytics-workers");
            p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
            p.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p)) {
                activeConsumer = consumer;
                consumer.subscribe(List.of(topic));
                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    if (!records.isEmpty()) {
                        // Payload is a wake-up hint only. All job state and tenant-scoped
                        // parameters are reloaded from PostgreSQL by AnalyticsJobStore.
                        signals.release(records.count());
                        consumer.commitSync();
                    }
                }
            } catch (WakeupException wakeup) {
                if (running.get()) System.err.println("Kafka analytics consumer interrupted; reconnecting");
            } catch (Exception unavailable) {
                if (running.get()) {
                    System.err.println("Kafka unavailable; PostgreSQL queue polling remains active: " + unavailable.getMessage());
                    try { Thread.sleep(2000); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            } finally {
                activeConsumer = null;
            }
        }
    }
    public void await(long timeoutMillis) throws InterruptedException {
        signals.tryAcquire(Math.max(1, timeoutMillis), java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    @Override public void close() {
        running.set(false);
        KafkaConsumer<String, String> consumer = activeConsumer;
        if (consumer != null) consumer.wakeup();
        if (consumerThread != null) {
            consumerThread.interrupt();
            try { consumerThread.join(3000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
}
