package pro.developia.circuitbreaker.service;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
class ExternalApiServiceTest {
    @Autowired
    private ExternalApiService externalApiService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static MockWebServer mockWebServer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("external.api.url", () -> mockWebServer.url("/").toString());
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void afterAll() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        mockWebServer.setDispatcher(new QueueDispatcher());

        circuitBreakerRegistry.circuitBreaker("externalApiService").reset();
    }

    @DisplayName("정상적인 상태")
    @Test
    void success() {
        final String message = "success";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .setBody(message));

        String result = externalApiService.callExternalApi();

        // 서킷 CLOSED 상태를 유지
        assertThat(result).isEqualTo(message);
        CircuitBreaker.State state = getServiceState();
        assertThat(state).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private CircuitBreaker.State getServiceState() {
        return circuitBreakerRegistry.circuitBreaker("externalApiService").getState();
    }

    @DisplayName("""
            임계점을 넘어서 요청이 실패한 경우 서킷 OPEN
            minimum-number-of-calls=10
            failure-rate-threshold=50%
            """)
    @Test
    void test2() {
        // given sliding-window-size 만큼 반환하도록 설정
        int initialRequestCount = mockWebServer.getRequestCount();
        IntStream.range(0, 10).forEach(i ->
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()))
        );

        // fallback 호출될 때를 위한 응답 (실제 호출 x)
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()).setBody("SHOULD_NOT_BE_CALLED"));

        // when 임계치를 넘길 만큼 실패를 유발
        // minimum-number-of-calls 10, failure-rate-threshold 50%
        for (int i = 0; i < 10; i++) {
            String result = externalApiService.callExternalApi();
            assertThat(result).contains("fallback");
        }

        // then 10번의 실패 이후 서킷이 오픈되고 fallback 호출
        CircuitBreaker.State state = getServiceState();
        assertThat(state).isEqualTo(CircuitBreaker.State.OPEN);
        String fallbackResult = externalApiService.callExternalApi();
        assertThat(fallbackResult).contains("fallback");

        // 요청이 들어오지 않았음을 확인
        assertThat(mockWebServer.getRequestCount()).isEqualTo(initialRequestCount + 10);
    }

    @DisplayName("""
            임계점을 넘어서 요청이 실패한 경우 서킷 OPEN, 10개 중 5개 실패
            minimum-number-of-calls=10
            failure-rate-threshold=50%
            """)
    @Test
    void test3() {
        // given sliding-window-size 만큼 반환하도록 설정
        int initialRequestCount = mockWebServer.getRequestCount();
        IntStream.range(0, 5).forEach(i ->
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()))
        );
        IntStream.range(0, 5).forEach(i ->
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()))
        );

        // fallback 호출될 때를 위한 응답 (실제 호출 x)
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()).setBody("SHOULD_NOT_BE_CALLED"));

        // when 임계치를 넘길 만큼 실패를 유발
        // minimum-number-of-calls 10, failure-rate-threshold 50%
        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        // then 10번의 실패 이후 서킷이 오픈되고 fallback 호출
        CircuitBreaker.State state = getServiceState();
        assertThat(state).isEqualTo(CircuitBreaker.State.OPEN);
        String fallbackResult = externalApiService.callExternalApi();
        assertThat(fallbackResult).contains("fallback");

        // 요청이 들어오지 않았음을 확인
        assertThat(mockWebServer.getRequestCount()).isEqualTo(initialRequestCount + 10);
    }

    @DisplayName("""
            임계점을 넘지 못해 서킷 CLOSED 상태, 10개 중 4개 실패
            minimum-number-of-calls=10
            failure-rate-threshold=50%
            """)
    @Test
    void test4() {
        // given sliding-window-size 만큼 반환하도록 설정
        int initialRequestCount = mockWebServer.getRequestCount();
        IntStream.range(0, 4).forEach(i ->
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()))
        );
        IntStream.range(0, 6).forEach(i ->
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()).setBody("SUCCESS"))
        );

        // fallback 호출될 때를 위한 응답 (실제 호출 o)
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()).setBody("SHOULD_BE_CALLED"));

        // when 임계치를 넘길 만큼 실패를 유발
        // minimum-number-of-calls 10, failure-rate-threshold 50%
        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        // then 10번의 실패 이후 서킷이 오픈되고 fallback 호출
        CircuitBreaker.State state = getServiceState();
        assertThat(state).isEqualTo(CircuitBreaker.State.CLOSED);
        String fallbackResult = externalApiService.callExternalApi();
        assertThat(fallbackResult).contains("SHOULD_BE_CALLED");

        // 요청이 들어오지 않았음을 확인
        assertThat(mockWebServer.getRequestCount()).isEqualTo(initialRequestCount + 11);
    }

    @DisplayName("""
            서킷 OPEN -> HALF_OPEN -> CLOSED
            wait-duration-in-open-state: 10s
            permitted-number-of-calls-in-half-open-state: 3
            failure-rate-threshold: 50
            """)
    @Test
    void test5() throws InterruptedException {
        // given
        IntStream.range(0, 10).forEach(i -> mockWebServer.enqueue(
                new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())));
        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()));
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()));
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()));

        log.info("=== wait-duration-in-open-state 10초 대기 ===");
        Thread.sleep(10 * 1000); // 10초

        // permitted-number-of-calls-in-half-open-state: 3
        // failure-rate-threshold: 50 이상일 경우 CLOSED
        externalApiService.callExternalApi();
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        externalApiService.callExternalApi();
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        externalApiService.callExternalApi();
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @DisplayName("""
            서킷 OPEN -> HALF_OPEN -> OPEN
            wait-duration-in-open-state: 10s
            permitted-number-of-calls-in-half-open-state: 3
            failure-rate-threshold: 50
            """)
    @Test
    void test6() throws InterruptedException {
        // given
        IntStream.range(0, 10).forEach(i -> mockWebServer.enqueue(
                new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())));
        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()));

        log.info("=== wait-duration-in-open-state 10초 대기 ===");
        Thread.sleep(10 * 1000); // 10초

        // permitted-number-of-calls-in-half-open-state: 3
        // failure-rate-threshold: 50 이상일 경우 CLOSED
        externalApiService.callExternalApi();
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        externalApiService.callExternalApi();
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        externalApiService.callExternalApi();
        assertThat(getServiceState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }
}
