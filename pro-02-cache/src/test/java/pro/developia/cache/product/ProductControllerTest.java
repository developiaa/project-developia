package pro.developia.cache.product;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StopWatch;

import java.util.Objects;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void tearDown() {
        log.info("--- Clearing all caches after test ---");
        cacheManager.getCacheNames().stream()
                .map(cacheManager::getCache)
                .filter(Objects::nonNull)
                .forEach(Cache::clear);
    }


    private void performCacheTest(Long productId, String cacheType) throws Exception {
        StopWatch stopWatch = new StopWatch();

        // 1. 첫 번째 호출 (캐시 MISS)
        log.info("[{}] --- 첫 번째 호출 시작 ---", cacheType);
        stopWatch.start("First Call");
        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId));
        stopWatch.stop();

        // 2. 두 번째 호출 (캐시 HIT)
        log.info("[{}] --- 두 번째 호출 시작 ---", cacheType);
        stopWatch.start("Second Call (Cached)");
        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId));
        stopWatch.stop();

        // 3. 결과 검증
        // ProductService의 findProductById 메서드가 단 1번만 호출되었는지 검증
        verify(productService, times(1)).findProductById(productId);

        log.info("[{}] 테스트 성능 결과:\n{}", cacheType, stopWatch.prettyPrint());
    }

    @Nested
    @ActiveProfiles("caffeine")
    @DisplayName("Caffeine 로컬 캐시 테스트")
    class CaffeineCacheTest {
        @Test
        @DisplayName("첫 호출은 서비스 메서드를 실행하고, 두 번째 호출은 캐시에서 결과를 반환한다.")
        void testCaffeineCache() throws Exception {
            Long productId = 11L;
            performCacheTest(productId, "Caffeine");
        }
    }

    @Nested
    @ActiveProfiles("ehcache")
    @DisplayName("Ehcache 로컬 캐시 테스트")
    class EhcacheCacheTest {
        @Test
        @DisplayName("첫 호출은 서비스 메서드를 실행하고, 두 번째 호출은 캐시에서 결과를 반환한다.")
        void testEhcacheCache() throws Exception {
            Long productId = 10L;
            performCacheTest(productId, "Ehcache");
        }
    }

    @Nested
    @ActiveProfiles("redis")
    @DisplayName("Redis 분산 캐시 테스트")
    class RedisCacheTest {
        @Test
        @DisplayName("첫 호출은 서비스 메서드를 실행하고, 두 번째 호출은 캐시에서 결과를 반환한다.")
        void testRedisCache() throws Exception {
            Long productId = 1L;
            performCacheTest(productId, "Redis");
        }
    }
}
