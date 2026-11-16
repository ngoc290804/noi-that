package vn.luma.api.admin;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.luma.api.catalog.CatalogRepository;
import static org.assertj.core.api.Assertions.*;
import static vn.luma.api.admin.AdminModels.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named="LUMA_DB_TESTS",matches="true")
@Transactional
class AdminDatabaseTest {
    @Autowired AdminRepository admin;
    @Autowired CatalogRepository catalog;
    @Autowired JdbcTemplate jdbc;
    private Category category(){return admin.createCategory(new CategoryInput("Danh mục kiểm thử","admin-test-category",true,10,0L));}
    private ProductInput input(Category c,long version,String status,String care){return new ProductInput("Ghế kiểm thử","admin-test-chair",c.id(),1230000L,"Giới thiệu","Mô tả",care,
        List.of(new ImageRef(admin.assets().get(0).id())),List.of(new SpecificationInput("Xuất xứ","Việt Nam",null)),1,true,status,
        new BigDecimal("80.25"),new BigDecimal("50"),new BigDecimal("90"),"Gỗ sồi","Nâu",version);}
    @Test void realCrudPreservesCareAndProtectsVersionsAndPublicVisibility(){
        var c=category();var product=admin.createProduct(input(c,0,"draft","Lau khăn mềm.\nTránh ngâm nước."));
        assertThat(catalog.getProduct(product.slug())).isEmpty();
        var published=admin.updateProduct(product.id(),input(c,product.version(),"published","Bảo quản mới."));
        assertThat(published.version()).isGreaterThan(product.version());
        var details=catalog.getProductDetails(published.slug()).orElseThrow();
        assertThat(details.careInstructions()).isEqualTo("Bảo quản mới.");
        assertThat(details.specifications().stream().filter(s->s.label().equals("Hướng dẫn bảo quản"))).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM luma.products p,LATERAL jsonb_array_elements(p.specifications) s WHERE p.id=? AND s->>'label'='Hướng dẫn bảo quản' AND s->>'value'=p.care_instructions",Integer.class,product.id())).isEqualTo(1);
        assertThatThrownBy(()->admin.updateProduct(product.id(),input(c,product.version(),"draft","Không được ghi đè")))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(admin.product(product.id()).careInstructions()).isEqualTo("Bảo quản mới.");
        admin.deleteProduct(product.id(),published.version());
        assertThat(catalog.getProduct(product.slug())).isEmpty();
        assertThat(admin.products()).noneMatch(p->p.id().equals(product.id()));
        admin.deleteCategory(c.id(),c.version());
        assertThat(admin.categories()).noneMatch(x->x.id().equals(c.id()));
    }
    @Test void cannotDeleteCategoryWithDraftProductsOrReferencedMedia(){
        var c=category();var p=admin.createProduct(input(c,0,"draft",""));
        assertThatThrownBy(()->admin.deleteCategory(c.id(),c.version())).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        var asset=p.images().get(0);
        assertThatThrownBy(()->admin.deleteAsset(asset.id(),asset.version())).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    @Test void metadataUpdatePropagatesAndInvalidatesOpenProductVersion(){
        var c=category();var p=admin.createProduct(input(c,0,"published","Giữ khô."));var a=p.images().get(0);
        var saved=admin.updateAsset(a.id(),new AssetInput("Mô tả ảnh đã chỉnh",a.version()));
        assertThat(saved.version()).isGreaterThan(a.version());
        assertThat(catalog.getProductImages(p.slug()).orElseThrow().get(0).alt()).isEqualTo("Mô tả ảnh đã chỉnh");
        assertThat(admin.product(p.id()).version()).isGreaterThan(p.version());
        assertThatThrownBy(()->admin.updateProduct(p.id(),input(c,p.version(),"published","Không ghi đè"))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void homeSaveUpdatesPublicContentAndRejectsStaleVersion(){
        var old=admin.home();var asset=admin.assets().get(0);
        var input=new HomeInput("Trang chủ đã chỉnh","Mô tả",new ImageRef(asset.id()),List.of(),List.of(new SpaceInput("Phòng đọc","Nhẹ nhàng",new ImageRef(asset.id()),"/san-pham")),old.version());
        var next=admin.updateHome(input);
        assertThat(catalog.getHomeHero().orElseThrow().heroTitle()).isEqualTo("Trang chủ đã chỉnh");
        assertThat(catalog.getHomeFeaturedProducts(12)).isEmpty();
        assertThat(next.version()).isGreaterThan(old.version());
        assertThatThrownBy(()->admin.updateHome(input)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void cannotSmuggleCareIntoSpecificationsOrUseUnknownImages(){
        var c=category();var original=input(c,0,"published","");
        var invalid=new ProductInput(original.name(),original.slug(),c.id(),0L,"","","",List.of(new ImageRef(UUID.randomUUID())),List.of(),0,false,"draft",null,null,null,null,null,0L);
        assertThatThrownBy(()->admin.createProduct(invalid)).isInstanceOf(ResponseStatusException.class);
        var duplicate=new ProductInput(original.name(),original.slug(),c.id(),0L,"","","",original.images(),List.of(new SpecificationInput("Hướng dẫn bảo quản","Sai vị trí",null)),0,false,"draft",null,null,null,null,null,0L);
        assertThatThrownBy(()->admin.createProduct(duplicate)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void standaloneLibraryAssetsCanBeDeletedAndNeverReturnedAgain(){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO luma.media_assets(id,src,thumbnail,alt,width,height) VALUES(?,?,?,?,?,?)",id,"/test-unused.webp","/test-unused.webp","Ảnh thử",100,80);
        var asset=admin.assets().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
        assertThat(asset.usedCount()).isZero();admin.deleteAsset(id,asset.version());
        assertThat(admin.assets()).noneMatch(a->a.id().equals(id));
    }
}
