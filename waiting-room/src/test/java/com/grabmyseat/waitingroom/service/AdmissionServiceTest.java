package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.client.InventorySaleClient;
import com.grabmyseat.waitingroom.dto.JoinQueueResponse;
import com.grabmyseat.waitingroom.repository.WaitlistOfferRepository;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
class AdmissionServiceTest {

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueService queueService;

    @Autowired
    private AdmissionService admissionService;

    @MockBean
    private WaitlistOfferRepository waitlistOfferRepository;

    @MockBean
    private InventorySaleClient inventorySaleClient;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        org.mockito.Mockito.doNothing().when(inventorySaleClient).requireQueueAccess(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void admitNext_removesHeadAndStoresPermit() {
        JoinQueueResponse response = queueService.join(1L, 100L);

        Optional<String> admitted = admissionService.admitNext(100L, queueService);

        assertThat(admitted).isPresent().hasValue(response.token());
        assertThat(queueService.queueSize(100L)).isZero();
        assertThat(queueService.permitValue(response.token())).isNotBlank();
    }

    @Test
    void admitNext_emptyQueueReturnsEmpty() {
        Optional<String> admitted = admissionService.admitNext(100L, queueService);

        assertThat(admitted).isEmpty();
    }

    @Test
    void admitNext_respectsRateLimit() {
        IntStream.rangeClosed(1, 15).forEach(i -> queueService.join((long) i, 100L));

        int admitted = 0;
        for (int i = 0; i < 15; i++) {
            Optional<String> token = admissionService.admitNext(100L, queueService);
            if (token.isPresent()) {
                admitted++;
            } else {
                break;
            }
        }

        assertThat(admitted).isEqualTo(10);
        assertThat(queueService.queueSize(100L)).isEqualTo(5L);
    }

    @Test
    void estimateWait_zeroPositionReturnsZero() {
        Long wait = admissionService.estimateWait(0L, 100L);
        assertThat(wait).isZero();
    }

    @Test
    void estimateWait_nonZeroPositionReturnsSeconds() {
        Long wait = admissionService.estimateWait(25L, 100L);
        assertThat(wait).isEqualTo(2L);
    }
}
