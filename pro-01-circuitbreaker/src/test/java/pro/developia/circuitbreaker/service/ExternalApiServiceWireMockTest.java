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

    private CircuitBreaker getCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("externalApiService");
    }

    private CircuitBreaker.State getServiceState() {
        return getCircuitBreaker().getState();
    }
}
