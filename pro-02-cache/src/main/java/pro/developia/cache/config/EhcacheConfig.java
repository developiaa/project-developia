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
        MutableConfiguration<Long, pro.developia.cache.product.Product> configuration =
                new MutableConfiguration<Long, pro.developia.cache.product.Product>()
                        .setTypes(Long.class, pro.developia.cache.product.Product.class)
                        .setStoreByValue(false)
                        .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.TEN_MINUTES));

        cacheManager.createCache("products", configuration);
    }
}
