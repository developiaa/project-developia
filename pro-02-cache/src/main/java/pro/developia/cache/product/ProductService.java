package pro.developia.cache.product;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ProductService {
    @Cacheable(value = "products", key = "#id")
    public Product findProductById(Long id) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("{}번 상품을 DB에서 조회합니다.", id);
        return new Product(id, "상품" + id, BigDecimal.valueOf(10_000), LocalDateTime.now());
    }
}
