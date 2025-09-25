package pro.developia.cache.config;

import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

@Profile("ehcache")
@Component
public class EhcacheConfig implements JCacheManagerCustomizer {

    @Override
    public void customize(CacheManager cacheManager) {
        cacheManager.createCache("products", buildCacheConfiguration(Duration.TEN_MINUTES));
    }

    private MutableConfiguration<Long, Object> buildCacheConfiguration(Duration ttl) {
        return new MutableConfiguration<Long, Object>()
                .setTypes(Long.class, Object.class)
                .setStoreByValue(false)
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(ttl));
    }
}
