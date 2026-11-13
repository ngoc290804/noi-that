package vn.luma.api.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static vn.luma.api.catalog.CatalogModels.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogRepository catalog;

    public CatalogController(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public ProductPage products(
        @RequestParam(defaultValue = "") @Size(max = 120) String q,
        @RequestParam(defaultValue = "") @Size(max = 100) String category,
        @RequestParam(defaultValue = "featured") @Size(max = 32) String sort,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size
    ) {
        return catalog.listProducts(q, category, sort, page, size);
    }

    @GetMapping("/products/{slug}")
    public Product product(@PathVariable @Size(max = 160) String slug) {
        return catalog.getProduct(slug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return catalog.listCategories();
    }

    @GetMapping("/home")
    public HomeContent home() {
        return catalog.getHomeContent()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nội dung trang chủ chưa được công khai."));
    }

    @GetMapping("/home/hero")
    public HomeHero homeHero() {
        return catalog.getHomeHero()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nội dung giới thiệu chưa được công khai."));
    }

    @GetMapping("/home/featured-products")
    public List<Product> homeFeaturedProducts(@RequestParam(defaultValue = "4") @Min(1) @Max(12) int size) {
        return catalog.getHomeFeaturedProducts(size);
    }

    @GetMapping("/home/spaces")
    public List<Space> homeSpaces() {
        return catalog.getHomeSpaces()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nội dung không gian sống chưa được công khai."));
    }
}
