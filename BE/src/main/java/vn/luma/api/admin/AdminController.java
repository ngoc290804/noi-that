package vn.luma.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static vn.luma.api.admin.AdminModels.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminRepository admin;
    public AdminController(AdminRepository admin){this.admin=admin;}
    @GetMapping("/catalog") public Catalog catalog(){return admin.catalog();}
    @GetMapping("/products") public List<Product> products(){return admin.products();}
    @GetMapping("/products/{id}") public Product product(@PathVariable UUID id){return admin.product(id);}
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@RequestBody @Valid ProductInput input){return admin.createProduct(input);}
    @PutMapping("/products/{id}") public Product updateProduct(@PathVariable UUID id,@RequestBody @Valid ProductInput input){return admin.updateProduct(id,input);}
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable UUID id,@RequestParam @Min(0) long version){admin.deleteProduct(id,version);}
    @PostMapping("/products/archive") public List<Product> archive(@RequestBody @Valid ArchiveInput input){return admin.archive(input);}
    @GetMapping("/categories") public List<Category> categories(){return admin.categories();}
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public Category createCategory(@RequestBody @Valid CategoryInput input){return admin.createCategory(input);}
    @PutMapping("/categories/{id}") public Category updateCategory(@PathVariable UUID id,@RequestBody @Valid CategoryInput input){return admin.updateCategory(id,input);}
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID id,@RequestParam @Min(0) long version){admin.deleteCategory(id,version);}
    @GetMapping("/media") public List<Asset> media(){return admin.assets();}
    @PutMapping("/media/{id}") public Asset updateAsset(@PathVariable UUID id,@RequestBody @Valid AssetInput input){return admin.updateAsset(id,input);}
    @DeleteMapping("/media/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAsset(@PathVariable UUID id,@RequestParam @Min(0) long version){admin.deleteAsset(id,version);}
    @GetMapping("/content") public HomeDocument content(){return admin.home();}
    @PutMapping("/content") public HomeDocument updateContent(@RequestBody @Valid HomeInput input){return admin.updateHome(input);}
}
