package vn.luma.api.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.luma.api.catalog.CatalogModels;
import static vn.luma.api.admin.AdminModels.*;

@Repository
@Transactional
public class AdminRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    public AdminRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Catalog catalog() {
        var media = assets();
        var home = home();
        return new Catalog(products(media), categories(), media, home.content(), home.version());
    }
    @Transactional(readOnly=true)
    public List<Product> products() { return products(assets()); }
    private List<Product> products(List<Asset> media) {
        var bySrc = new HashMap<String, Asset>();
        media.forEach(a -> bySrc.put(a.src(), a));
        return jdbc.query("SELECT * FROM luma.products WHERE deleted_at IS NULL ORDER BY display_order,id",
            (rs,n) -> mapProduct(rs, bySrc));
    }
    @Transactional(readOnly=true)
    public Product product(UUID id) {
        return products().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> missing("sản phẩm"));
    }
    @Transactional(readOnly=true)
    public List<Category> categories() {
        return jdbc.query("SELECT * FROM luma.categories WHERE deleted_at IS NULL ORDER BY display_order,id", (rs,n) ->
            new Category(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("slug"),rs.getBoolean("is_published"),rs.getInt("display_order"),rs.getLong("version")));
    }
    @Transactional(readOnly=true)
    public List<Asset> assets() {
        return jdbc.query("""
            SELECT m.*,
                (SELECT count(*) FROM luma.products p WHERE p.deleted_at IS NULL
                    AND EXISTS(SELECT 1 FROM jsonb_array_elements(p.images) i WHERE i->>'src'=m.src))
                + (SELECT count(*) FROM luma.home_content h WHERE h.content->'heroImage'->>'src'=m.src
                    OR EXISTS(SELECT 1 FROM jsonb_array_elements(coalesce(h.content->'spaces','[]')) s WHERE s->'image'->>'src'=m.src)) AS used_count
            FROM luma.media_assets m WHERE m.deleted_at IS NULL ORDER BY m.created_at DESC,m.id
            """, (rs,n) -> asset(rs,rs.getLong("used_count")));
    }
    @Transactional(readOnly=true)
    public HomeDocument home() {
        return jdbc.query("SELECT content::text,version FROM luma.home_content WHERE code='home'", (rs,n) ->
            new HomeDocument(decode(rs.getString(1),new TypeReference<CatalogModels.HomeContent>(){}),rs.getLong(2)))
            .stream().findFirst().orElseThrow(() -> missing("nội dung trang chủ"));
    }

    public Product createProduct(ProductInput input) { return writeProduct(UUID.randomUUID(), input, true); }
    public Product updateProduct(UUID id, ProductInput input) { return writeProduct(id, input, false); }
    private Product writeProduct(UUID id, ProductInput input, boolean create) {
        lockWrites();
        if (create && input.version()!=0) throw conflict();
        lockCategory(input.categoryId());
        var images = resolveImages(input.images());
        if (input.status().equals("published") && images.isEmpty()) throw bad("Sản phẩm hiển thị cần ít nhất một ảnh.");
        var labels = new HashSet<String>();
        for (var s:input.specifications()) {
            String label = normalize(s.label());
            if (Set.of("huong dan bao quan","bao quan","chat lieu","mau sac","kich thuoc").contains(label))
                throw bad("Nhập bảo quản, chất liệu, màu sắc và kích thước tại trường riêng, không nhập lặp trong thông số bổ sung.");
            if (!labels.add(label)) throw bad("Tên thông số bổ sung không được trùng nhau.");
        }
        var specs = input.specifications().stream().map(s -> new CatalogModels.Specification(s.label().strip(),s.value().strip(),s.unit())).toList();
        var values = new MapSqlParameterSource().addValue("id",id).addValue("name",input.name().strip()).addValue("slug",input.slug())
            .addValue("category",input.categoryId()).addValue("price",input.priceVnd()).addValue("short",input.shortDescription().strip())
            .addValue("description",input.description().strip()).addValue("care",input.careInstructions().strip())
            .addValue("images",encode(images)).addValue("specs",encode(specs)).addValue("order",input.displayOrder())
            .addValue("featured",input.isFeatured()).addValue("status",input.status()).addValue("published",input.status().equals("published"))
            .addValue("length",input.lengthCm()).addValue("width",input.widthCm()).addValue("height",input.heightCm())
            .addValue("material",blankToNull(input.material())).addValue("color",blankToNull(input.color())).addValue("version",input.version());
        if (create) jdbc.update("""
            INSERT INTO luma.products(id,name,slug,category_id,price_vnd,short_description,description,care_instructions,
                images,specifications,display_order,is_featured,status,is_published,length_cm,width_cm,height_cm,material,color)
            VALUES(:id,:name,:slug,:category,:price,:short,:description,:care,CAST(:images AS jsonb),CAST(:specs AS jsonb),
                :order,:featured,:status,:published,:length,:width,:height,:material,:color)
            """, values);
        else {
            int count=jdbc.update("""
                UPDATE luma.products SET name=:name,slug=:slug,category_id=:category,price_vnd=:price,
                    short_description=:short,description=:description,care_instructions=:care,images=CAST(:images AS jsonb),
                    specifications=CAST(:specs AS jsonb),display_order=:order,is_featured=:featured,status=:status,
                    is_published=:published,length_cm=:length,width_cm=:width,height_cm=:height,material=:material,color=:color
                WHERE id=:id AND version=:version AND deleted_at IS NULL
                """,values);
            requireChanged("products",id,count);
        }
        syncFeaturedIds();
        return product(id);
    }
    public void deleteProduct(UUID id,long version) {
        lockWrites();
        int count=jdbc.update("UPDATE luma.products SET deleted_at=clock_timestamp(),is_published=false WHERE id=:id AND version=:version AND deleted_at IS NULL", Map.of("id",id,"version",version));
        requireChanged("products",id,count);
        syncFeaturedIds();
    }
    public List<Product> archive(ArchiveInput input) {
        lockWrites();
        var sorted=input.products().stream().sorted(Comparator.comparing(ProductVersion::id)).toList();
        if (sorted.stream().map(ProductVersion::id).distinct().count()!=sorted.size()) throw bad("Danh sách sản phẩm bị trùng.");
        for (var p:sorted) requireChanged("products",p.id(),jdbc.update("UPDATE luma.products SET status='archived',is_published=false WHERE id=:id AND version=:version AND deleted_at IS NULL",Map.of("id",p.id(),"version",p.version())));
        syncFeaturedIds();
        return products();
    }
    public Category createCategory(CategoryInput input) { return writeCategory(UUID.randomUUID(),input,true); }
    public Category updateCategory(UUID id,CategoryInput input) { return writeCategory(id,input,false); }
    private Category writeCategory(UUID id,CategoryInput input,boolean create) {
        lockWrites();
        var args=new MapSqlParameterSource().addValue("id",id).addValue("name",input.name().strip()).addValue("slug",input.slug())
            .addValue("active",input.active()).addValue("order",input.displayOrder()).addValue("version",input.version());
        if(create) {
            if(input.version()!=0) throw conflict();
            jdbc.update("INSERT INTO luma.categories(id,name,slug,is_published,display_order) VALUES(:id,:name,:slug,:active,:order)",args);
        } else requireChanged("categories",id,jdbc.update("UPDATE luma.categories SET name=:name,slug=:slug,is_published=:active,display_order=:order WHERE id=:id AND version=:version AND deleted_at IS NULL",args));
        return categories().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
    public void deleteCategory(UUID id,long version) {
        lockWrites();
        lockVersion("categories",id,version);
        Long count=jdbc.queryForObject("SELECT count(*) FROM luma.products WHERE category_id=:id AND deleted_at IS NULL",Map.of("id",id),Long.class);
        if(count!=null && count>0) throw badConflict("Danh mục còn sản phẩm. Hãy chuyển sản phẩm sang danh mục khác trước khi xóa.");
        jdbc.update("UPDATE luma.categories SET deleted_at=clock_timestamp(),is_published=false WHERE id=:id",Map.of("id",id));
    }
    public HomeDocument updateHome(HomeInput input) {
        lockWrites();
        // The home row is the common lock for content and featured-product changes.
        var versions=jdbc.query("SELECT version FROM luma.home_content WHERE code='home' FOR UPDATE",(rs,n)->rs.getLong(1));
        if(versions.isEmpty()) throw missing("nội dung trang chủ");
        if(versions.get(0).longValue()!=input.version()) throw conflict();
        if(new HashSet<>(input.featuredProductIds()).size()!=input.featuredProductIds().size()) throw bad("Sản phẩm nổi bật bị trùng.");
        var selected=new HashSet<>(input.featuredProductIds());
        if(!selected.isEmpty()) {
            int found=jdbc.query("SELECT id FROM luma.products WHERE id IN (:ids) AND deleted_at IS NULL",Map.of("ids",selected),(rs,n)->rs.getObject(1,UUID.class)).size();
            if(found!=selected.size()) throw bad("Danh sách nổi bật chứa sản phẩm không còn tồn tại.");
        }
        var hero=resolveImage(input.heroImage());
        var spaces=input.spaces().stream().map(s->new CatalogModels.Space(s.title().strip(),s.description().strip(),resolveImage(s.image()),s.href())).toList();
        var content=new CatalogModels.HomeContent(input.heroTitle().strip(),input.heroDescription().strip(),hero,List.copyOf(selected),spaces);
        // Only touch changed flags, preserving versions for unrelated products.
        for(var p:products()) if(p.isFeatured()!=selected.contains(p.id()))
            jdbc.update("UPDATE luma.products SET is_featured=:featured WHERE id=:id",Map.of("id",p.id(),"featured",selected.contains(p.id())));
        jdbc.update("UPDATE luma.home_content SET content=CAST(:content AS jsonb),is_published=true WHERE code='home'",Map.of("content",encode(content)));
        return home();
    }
    public Asset updateAsset(UUID id,AssetInput input) {
        lockWrites();
        lockVersion("media_assets",id,input.version());
        var current=assets().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
        var args=Map.of("id",id,"src",current.src(),"alt",input.alt().strip());
        // Update all gallery/home references atomically; no stale alt copies remain.
        jdbc.update("UPDATE luma.media_assets SET alt=:alt WHERE id=:id",args);
        jdbc.update("""
            UPDATE luma.products p SET images=(SELECT jsonb_agg(CASE WHEN i->>'src'=:src THEN jsonb_set(i,'{alt}',to_jsonb(CAST(:alt AS text))) ELSE i END ORDER BY ord)
                FROM jsonb_array_elements(p.images) WITH ORDINALITY e(i,ord))
            WHERE deleted_at IS NULL AND EXISTS(SELECT 1 FROM jsonb_array_elements(p.images) i WHERE i->>'src'=:src)
            """,args);
        jdbc.update("""
            UPDATE luma.home_content SET content=jsonb_set(
                CASE WHEN content->'heroImage'->>'src'=:src THEN jsonb_set(content,'{heroImage,alt}',to_jsonb(CAST(:alt AS text))) ELSE content END,
                '{spaces}',coalesce((SELECT jsonb_agg(CASE WHEN s->'image'->>'src'=:src THEN jsonb_set(s,'{image,alt}',to_jsonb(CAST(:alt AS text))) ELSE s END ORDER BY ord)
                    FROM jsonb_array_elements(coalesce(content->'spaces','[]')) WITH ORDINALITY e(s,ord)),'[]'::jsonb))
            WHERE content->'heroImage'->>'src'=:src OR EXISTS(SELECT 1 FROM jsonb_array_elements(coalesce(content->'spaces','[]')) s WHERE s->'image'->>'src'=:src)
            """,args);
        return assets().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
    }
    public void deleteAsset(UUID id,long version) {
        lockWrites();
        lockVersion("media_assets",id,version);
        var current=assets().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
        if(current.usedCount()>0) throw badConflict("Ảnh đang được sản phẩm hoặc nội dung sử dụng. Gỡ các tham chiếu trước khi xóa.");
        jdbc.update("UPDATE luma.media_assets SET deleted_at=clock_timestamp() WHERE id=:id",Map.of("id",id));
    }

    private void lockWrites() { jdbc.query("SELECT pg_advisory_xact_lock(1886745197)", (rs,n) -> 0); }
    private void syncFeaturedIds() {
        jdbc.update("""
            UPDATE luma.home_content SET content=jsonb_set(content,'{featuredProductIds}',
                coalesce((SELECT jsonb_agg(to_jsonb(id) ORDER BY display_order,id) FROM luma.products WHERE deleted_at IS NULL AND is_featured),'[]'::jsonb))
            WHERE code='home' AND content->'featuredProductIds' IS DISTINCT FROM
                coalesce((SELECT jsonb_agg(to_jsonb(id) ORDER BY display_order,id) FROM luma.products WHERE deleted_at IS NULL AND is_featured),'[]'::jsonb)
            """,Map.of());
    }
    private void lockCategory(UUID id) {
        if(jdbc.query("SELECT id FROM luma.categories WHERE id=:id AND deleted_at IS NULL FOR SHARE",Map.of("id",id),(rs,n)->rs.getObject(1)).isEmpty()) throw bad("Danh mục không tồn tại hoặc đã bị xóa.");
    }
    private void lockVersion(String table,UUID id,long version) {
        // Table identifiers only originate from fixed internal literals.
        var rows=jdbc.query("SELECT version FROM luma."+table+" WHERE id=:id AND deleted_at IS NULL FOR UPDATE",Map.of("id",id),(rs,n)->rs.getLong(1));
        if(rows.isEmpty()) throw missing("bản ghi");
        if(rows.get(0)!=version) throw conflict();
    }
    private List<CatalogModels.ProductImage> resolveImages(List<ImageRef> refs) {
        if(refs.stream().map(ImageRef::id).distinct().count()!=refs.size()) throw bad("Danh sách ảnh không được trùng nhau.");
        // Stable ordering prevents lock inversion when editors select the same assets.
        var resolved=new HashMap<UUID,CatalogModels.ProductImage>();
        refs.stream().map(ImageRef::id).sorted().forEach(id->resolved.put(id,resolveImage(new ImageRef(id))));
        return refs.stream().map(ref->resolved.get(ref.id())).toList();
    }
    private CatalogModels.ProductImage resolveImage(ImageRef ref) {
        var rows=jdbc.query("SELECT * FROM luma.media_assets WHERE id=:id AND deleted_at IS NULL FOR SHARE",Map.of("id",ref.id()),(rs,n)->asset(rs,0));
        if(rows.isEmpty()) throw bad("Ảnh không tồn tại hoặc đã bị xóa. Tải lại thư viện ảnh.");
        var a=rows.get(0);
        return new CatalogModels.ProductImage(a.src(),a.thumbnail(),a.alt(),a.width(),a.height(),a.publicId());
    }
    private Product mapProduct(ResultSet rs,Map<String,Asset> media) throws SQLException {
        var images=decode(rs.getString("images"),new TypeReference<List<CatalogModels.ProductImage>>(){}).stream().map(i->{
            var a=media.get(i.src());
            if(a==null) throw new IllegalStateException("Catalog image is missing from the media library");
            return a;
        }).toList();
        var specs=decode(rs.getString("specifications"),new TypeReference<List<CatalogModels.Specification>>(){}).stream()
            .filter(s->!Set.of("huong dan bao quan","bao quan").contains(normalize(s.label())))
            .filter(s-> !(normalize(s.label()).equals("chat lieu") && rsString(rs,"material")!=null))
            .filter(s-> !(normalize(s.label()).equals("mau sac") && rsString(rs,"color")!=null))
            .filter(s-> !(normalize(s.label()).equals("kich thuoc") && rsDecimal(rs,"length_cm")!=null && rsDecimal(rs,"width_cm")!=null && rsDecimal(rs,"height_cm")!=null))
            .filter(s->s.value()!=null && !s.value().isBlank())
            .map(s->normalize(s.label()).equals("kich thuoc") ? new CatalogModels.Specification("Kích thước mô tả",s.value(),s.unit()) : s).toList();
        return new Product(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("slug"),rs.getObject("category_id",UUID.class),rs.getLong("price_vnd"),
            rs.getString("short_description"),rs.getString("description"),rs.getString("care_instructions"),images,specs,rs.getInt("display_order"),
            rs.getBoolean("is_featured"),rs.getString("status"),rs.getBigDecimal("length_cm"),rs.getBigDecimal("width_cm"),rs.getBigDecimal("height_cm"),
            rs.getString("material"),rs.getString("color"),rs.getTimestamp("updated_at").toInstant(),rs.getLong("version"));
    }
    private static String rsString(ResultSet rs,String field) { try{return blankToNull(rs.getString(field));}catch(SQLException e){throw new IllegalStateException(e);} }
    private static java.math.BigDecimal rsDecimal(ResultSet rs,String field) { try{return rs.getBigDecimal(field);}catch(SQLException e){throw new IllegalStateException(e);} }
    private static String blankToNull(String value) { return value==null || value.isBlank()?null:value.strip(); }
    private static String normalize(String value) { return java.text.Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replaceAll("\\s+"," "); }
    private Asset asset(ResultSet rs,long used) throws SQLException {
        return new Asset(rs.getObject("id",UUID.class),rs.getString("src"),rs.getString("thumbnail"),rs.getString("public_id"),rs.getString("alt"),rs.getInt("width"),rs.getInt("height"),rs.getLong("version"),used);
    }
    private void requireChanged(String table,UUID id,int count) {
        if(count!=0)return;
        Boolean exists=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM luma."+table+" WHERE id=:id AND deleted_at IS NULL)",Map.of("id",id),Boolean.class);
        if(Boolean.TRUE.equals(exists))throw conflict();
        throw missing("bản ghi");
    }
    private <T>T decode(String value,TypeReference<T> type){try{return json.readValue(value,type);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid stored admin content",e);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot encode admin content",e);}}
    private static ResponseStatusException missing(String entity){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy "+entity+".");}
    private static ResponseStatusException conflict(){return badConflict("Dữ liệu đã được thay đổi ở phiên khác. Tải lại danh sách rồi mở lại bản ghi để đối chiếu; nội dung đang nhập vẫn được giữ.");}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException badConflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
