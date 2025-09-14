package pro.developia.circuitbreaker.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RequiredArgsConstructor
@Service
public class ExternalApiService {
    private final RestTemplate restTemplate;
    @Value("${external.api.url}")
    private String externalApiUrl;


    @CircuitBreaker(name = "externalApiService", fallbackMethod = "fallback")
    public String callExternalApi() {
        log.info("=== callExternalApi ===");
        return restTemplate.getForObject(externalApiUrl + "/api/data", String.class);
    }

    private String fallback(Throwable t) {
        log.warn("== fallback {}", t.getClass().getSimpleName());

        if (t instanceof HttpServerErrorException) {
            return "fallback(HttpServerErrorException)";
        }
        return "fallback";
    }
}
