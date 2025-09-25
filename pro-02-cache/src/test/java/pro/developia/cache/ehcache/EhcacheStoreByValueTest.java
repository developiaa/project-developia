package pro.developia.cache.ehcache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class EhcacheStoreByValueTest {

    @Test
    @Profile("ehcache")
    @DisplayName("setStoreByValue(false): 캐시 객체를 수정하면 원본도 변경 (by Reference)")
    void test1() {
        CachingProvider cachingProvider = Caching.getCachingProvider();
        CacheManager cacheManager = cachingProvider.getCacheManager();

        MutableConfiguration<Long, Product> config = new MutableConfiguration<Long, Product>()
                .setTypes(Long.class, Product.class)
                .setStoreByValue(false)
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.TEN_MINUTES));

        Cache<Long, Product> cache = cacheManager.createCache("byValueCache", config);
        Product originalProduct = new Product(1L, "original");
        cache.put(1L, originalProduct);
        Product productFromCache = cache.get(1L);

        productFromCache.setName("modified");

        assertThat(originalProduct.getName()).isEqualTo("modified");
        assertThat(originalProduct).isSameAs(productFromCache);

        cacheManager.destroyCache("byValueCache");
        cachingProvider.close();
    }

    @Test
    @Profile("ehcache")
    @DisplayName("setStoreByValue(true): 캐시 객체를 수정해도 원본은 불변 (by Value)")
    void test2() {
        CachingProvider cachingProvider = Caching.getCachingProvider();
        CacheManager cacheManager = cachingProvider.getCacheManager();

        MutableConfiguration<Long, Product> config = new MutableConfiguration<Long, Product>()
                .setTypes(Long.class, Product.class)
                .setStoreByValue(true)
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.TEN_MINUTES));

        Cache<Long, Product> cache = cacheManager.createCache("byReferenceCache", config);

        Product originalProduct = new Product(1L, "original");
        cache.put(1L, originalProduct);

        Product productFromCache = cache.get(1L);
        productFromCache.setName("modified");

        assertThat(originalProduct.getName()).isEqualTo("original");
        assertThat(productFromCache.getName()).isEqualTo("modified");
        assertThat(originalProduct).isNotSameAs(productFromCache);

        cacheManager.destroyCache("byReferenceCache");
        cachingProvider.close();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Product implements Serializable {
        private Long id;
        private String name;
    }
}
