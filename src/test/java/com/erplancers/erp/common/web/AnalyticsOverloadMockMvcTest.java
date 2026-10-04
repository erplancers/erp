package com.erplancers.erp.common.web;

import com.erplancers.erp.common.security.domain.ErpUser;
import com.erplancers.erp.common.security.repository.ErpAccessRuleRepository;
import com.erplancers.erp.common.security.repository.ErpUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsOverloadMockMvcTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ErpUserRepository users;

    @Autowired
    ErpAccessRuleRepository policies;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        policies.deleteAll();
        users.deleteAll();

        ErpUser admin = new ErpUser();
        admin.setUsername("load-admin");
        admin.setPassword(passwordEncoder.encode("load-pass"));
        admin.setLevel("ADMIN");
        admin.getRoles().add("ADMIN");
        users.save(admin);
    }

    @Test
    void analyticsEndpointsHandleConcurrentMockMvcLoad() throws Exception {
        int concurrency = 4;
        int requestsPerWorker = 4;
        int expectedRequests = concurrency * requestsPerWorker;

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successful = new AtomicInteger();

        List<Callable<Void>> workers = new ArrayList<>(concurrency);

        for (int worker = 0; worker < concurrency; worker++) {
            final int workerId = worker;
            workers.add(() -> {
                ready.countDown();

                if (!start.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Load-test workers did not start");
                }

                for (int i = 0; i < requestsPerWorker; i++) {
                    performEndpoint((workerId + i) % 5);
                    successful.incrementAndGet();
                }

                return null;
            });
        }

        try {
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Load-test workers did not become ready");
            }

            long started = System.nanoTime();
            start.countDown();

            List<Future<Void>> futures = executor.invokeAll(workers, 90, TimeUnit.SECONDS);
            for (Future<Void> future : futures) {
                future.get();
            }

            long elapsedMillis =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            if (successful.get() != expectedRequests) {
                throw new AssertionError(String.format(
                        "Expected %d successful requests but got %d in %d ms",
                        expectedRequests,
                        successful.get(),
                        elapsedMillis));
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void performEndpoint(int endpoint) throws Exception {
        if (endpoint == 0) {
            mvc.perform(get("/api/analytics/resources")
                            .with(httpBasic("load-admin", "load-pass")))
                    .andExpect(status().isOk());
        } else if (endpoint == 1) {
            mvc.perform(get("/api/analytics/catalog")
                            .with(httpBasic("load-admin", "load-pass")))
                    .andExpect(status().isOk());
        } else if (endpoint == 2) {
            mvc.perform(get("/api/analytics/dashboard")
                            .with(httpBasic("load-admin", "load-pass")))
                    .andExpect(status().isOk());
        } else if (endpoint == 3) {
            mvc.perform(get("/api/analytics/resources/invoice/aggregate")
                            .param("metrics", "count")
                            .param("limit", "25")
                            .with(httpBasic("load-admin", "load-pass")))
                    .andExpect(status().isOk());
        } else {
            mvc.perform(get("/api/analytics/resources/invoice/profile")
                            .param("topValues", "5")
                            .with(httpBasic("load-admin", "load-pass")))
                    .andExpect(status().isOk());
        }
    }
}
