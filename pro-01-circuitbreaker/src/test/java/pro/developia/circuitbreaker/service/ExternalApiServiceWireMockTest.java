package pro.developia.circuitbreaker.service;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class ExternalApiServiceWireMockTest {

    @Autowired
    private ExternalApiService externalApiService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("external.api.url", () -> "http://localhost:${wiremock.server.port}");
    }

    @BeforeEach
    void setUp() {
        WireMock.reset();
        circuitBreakerRegistry.circuitBreaker("externalApiService").reset();
    }

    @DisplayName("정상적인 상태")
    @Test
    void success() {
        stubFor(get("/api/data")
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody("success")));

        String result = externalApiService.callExternalApi();

        assertThat(result).isEqualTo("success");
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(1, getRequestedFor(urlEqualTo("/api/data")));
    }

    @DisplayName("""
            임계점을 넘어서 요청이 실패한 경우 서킷 OPEN
            minimum-number-of-calls=10
            failure-rate-threshold=50%
            """)
    @Test
    void test2() {
        stubFor(get("/api/data")
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(10, getRequestedFor(urlEqualTo("/api/data")));
    }

    @DisplayName("""
            임계점을 넘어서 요청이 실패한 경우 서킷 OPEN, 10개 중 5개 실패
            minimum-number-of-calls=10
            failure-rate-threshold=50%
            """)
    @Test
    void test3() {
        // given 처음 5번은 에러, 이후에는 정상
        String scenarioName = "scenario";
        String successState = "SUCCESS_STATE";

        // STARTED -> Failure_2 -> ... -> Failure_5 -> SUCCESS_STATE
        String currentState = Scenario.STARTED;
        for (int i = 1; i <= 5; i++) {
            String nextState = (i == 5) ? successState : "Failure_" + (i + 1);
            stubFor(get("/api/data")
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(currentState)
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                    .willSetStateTo(nextState));
            currentState = nextState;
        }

        stubFor(get("/api/data")
                .inScenario(scenarioName)
                .whenScenarioStateIs(successState)
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody("SUCCESS")));

        // when 10번 호출 (앞 5번은 실패, 뒤 5번은 성공)
        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        // then 실패율이 50%이므로 서킷은 OPEN 상태가 됨
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.OPEN);
        // 총 10번의 요청이 정상적으로 갔는지 확인
        verify(10, getRequestedFor(urlEqualTo("/api/data")));
    }

    @DisplayName("""
            임계점을 넘지 못해 서킷 CLOSED 상태, 10개 중 4개 실패
            minimum-number-of-calls=10
            failure-rate-threshold=50%
            """)
    @Test
    void test4() {
        // given 처음 4번은 에러, 이후에는 정상
        String scenarioName = "scenario";
        String successState = "SUCCESS_STATE";

        String currentState = Scenario.STARTED;
        for (int i = 1; i <= 4; i++) {
            String nextState = (i == 4) ? successState : "Failure_" + (i + 1);
            stubFor(get("/api/data")
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(currentState)
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                    .willSetStateTo(nextState));
            currentState = nextState;
        }

        stubFor(get("/api/data")
                .inScenario(scenarioName)
                .whenScenarioStateIs(successState)
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody("SUCCESS")));

        // when 10번 호출 (앞 4번은 실패, 뒤 6번은 성공)
        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        // then 실패율이 40%이므로 서킷은 CLOSED 상태
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(10, getRequestedFor(urlEqualTo("/api/data")));
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
        String scenarioName = "scenario";
        stubFor(get("/api/data")
                .inScenario(scenarioName)
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        stubFor(get("/api/data")
                .inScenario(scenarioName)
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())));

        log.info("=== wait-duration-in-open-state 10초 대기 ===");
        Thread.sleep(10 * 1000);

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
        stubFor(get("/api/data")
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        String scenarioName = "scenario";
        stubFor(get("/api/data")
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .willSetStateTo("Second_Trial"));

        stubFor(get("/api/data")
                .inScenario(scenarioName)
                .whenScenarioStateIs("Second_Trial")
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .willSetStateTo("Third_Trial"));

        stubFor(get("/api/data")
                .inScenario(scenarioName)
                .whenScenarioStateIs("Third_Trial")
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));


        log.info("=== wait-duration-in-open-state 10초 대기 ===");
        Thread.sleep(10 * 1000 + 500); // 10.5초 버퍼

        // then
        // 1. 첫 번째 시험 요청 (실패) -> 아직 3개를 다 채우지 않았으므로 HALF_OPEN 유지
        externalApiService.callExternalApi();
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 2. 두 번째 시험 요청 (실패) -> 아직 3개를 다 채우지 않았으므로 HALF_OPEN 유지
        externalApiService.callExternalApi();
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 3. 세 번째 시험 요청 (성공) -> 3개를 모두 채웠으므로 최종 판정
        // 최종 판정: 3번 중 2번 실패 (실패율 66.7%) -> 50% 임계치를 넘었으므로 OPEN으로 전환
        externalApiService.callExternalApi();
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.OPEN);
    }


    @DisplayName("""
            slow call로 인한 서킷 OPEN
            slow-call-rate-threshold: 50
            slow-call-duration-threshold: 1000ms
            """)
    @Test
    void test7() {
        stubFor(get("/api/data")
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody("SLOW_SUCCESS")
                        .withFixedDelay(1001)));

        for (int i = 0; i < 10; i++) {
            externalApiService.callExternalApi();
        }

        CircuitBreaker.Metrics metrics = getCircuitBreaker().getMetrics();
        assertThat(metrics.getSlowCallRate()).isEqualTo(100.0f);
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(10, getRequestedFor(urlEqualTo("/api/data")));
    }

    @DisplayName("4xx 에러는 실패로 기록되지 않는다")
    @Test
    void test8() {
        stubFor(get("/api/data")
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())
                        .withBody("Not Found")));

        for (int i = 0; i < 10; i++) {
            String result = externalApiService.callExternalApi();
            assertThat(result).contains("fallback");
        }

        assertThat(getCircuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(10, getRequestedFor(urlEqualTo("/api/data")));
    }

    @DisplayName("5xx 에러 발생 시 실패로 기록 o, 서킷 OPEN")
    @Test
    void test9() {
        stubFor(get("/api/data")
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withBody("Internal Server Error")));

        for (int i = 0; i < 10; i++) {
            String result = externalApiService.callExternalApi();
            assertThat(result).contains("fallback");
        }

        assertThat(getCircuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(10);
        assertThat(getServiceState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(10, getRequestedFor(urlEqualTo("/api/data")));
    }

    private CircuitBreaker getCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("externalApiService");
    }

    private CircuitBreaker.State getServiceState() {
        return getCircuitBreaker().getState();
    }
}
