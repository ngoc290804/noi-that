package vn.luma.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import vn.luma.api.auth.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, PasswordConfig.class})
@WebMvcTest(value=AdminController.class,properties={"app.media.upload-token=admin-test-token-at-least-32-characters","app.cors.allowed-origins=http://127.0.0.1:5174"})
class AdminControllerTest {
    private static final String AUTH="Bearer admin-test-token-at-least-32-characters";
    @MockitoBean AccountService accounts;
    @MockitoBean LoginAttemptService attempts;
    @BeforeEach void authenticatedAccount() {
        org.mockito.Mockito.when(accounts.byUsername("editor")).thenReturn(new AccountModels.Account(java.util.UUID.randomUUID(),"editor","Nhân viên","EDITOR",true,false,0,java.time.Instant.now(),null));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean AdminRepository admin;
    private Map<String,Object> input(){
        var input=new HashMap<String,Object>();
        input.put("name","Ghế ăn");input.put("slug","ghe-an");input.put("categoryId",UUID.randomUUID());input.put("priceVnd",1000000);
        input.put("shortDescription","");input.put("description","");input.put("careInstructions","Lau nhẹ bằng khăn mềm.");
        input.put("images",List.of());input.put("specifications",List.of());input.put("displayOrder",0);input.put("status","draft");input.put("version",0);
        return input;
    }
    @Test void protectsAllReadAndWriteRoutes() throws Exception {
        String id=UUID.randomUUID().toString();
        mvc.perform(get("/api/admin/catalog")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/products").with(csrf()).contentType("application/json").content(json.writeValueAsString(input()))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/categories/"+id).with(csrf()).header("Authorization","Bearer wrong").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/products/"+id).with(csrf()).param("version","0")).andExpect(status().isUnauthorized());
        verifyNoInteractions(admin);
    }
    @Test void acceptsValidatedProductCreateAndUpdate() throws Exception {
        var body=input();
        mvc.perform(post("/api/admin/products").with(user("editor").roles("EDITOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isCreated());
        UUID id=UUID.randomUUID();
        mvc.perform(put("/api/admin/products/"+id).with(user("editor").roles("EDITOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        verify(admin).createProduct(argThat(p->p.careInstructions().equals("Lau nhẹ bằng khăn mềm.")&&p.version()==0));
        verify(admin).updateProduct(eq(id),any());
    }
    @Test void rejectsInvalidPayloadsWithoutRepositoryWrites() throws Exception {
        for(var change:List.of(Map.entry("priceVnd",(Object)(-1)),Map.entry("slug",(Object)"invalid/slug"),Map.entry("lengthCm",(Object)0),Map.entry("version",(Object)(-1)),Map.entry("careInstructions",(Object)"x".repeat(20001)),Map.entry("images",(Object)Arrays.asList((Object)null)))) {
            var body=input();body.put(change.getKey(),change.getValue());
            mvc.perform(post("/api/admin/products").with(user("editor").roles("EDITOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").exists());
        }
        var body=input();body.remove("version");
        mvc.perform(post("/api/admin/products").with(user("editor").roles("EDITOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }
    @Test void deleteRequiresUuidAndVersion() throws Exception {
        mvc.perform(delete("/api/admin/products/not-uuid").with(user("editor").roles("EDITOR")).with(csrf()).param("version","0")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/products/"+UUID.randomUUID()).with(user("editor").roles("EDITOR")).with(csrf())).andExpect(status().isBadRequest());
        UUID id=UUID.randomUUID();
        mvc.perform(delete("/api/admin/products/"+id).with(user("editor").roles("EDITOR")).with(csrf()).param("version","2")).andExpect(status().isNoContent());
        verify(admin).deleteProduct(id,2);
    }
    @Test void corsAllowsOnlyConfiguredAdminOriginsAndMethods() throws Exception {
        mvc.perform(options("/api/admin/products/"+UUID.randomUUID()).header("Origin","http://127.0.0.1:5174").header("Access-Control-Request-Method","DELETE").header("Access-Control-Request-Headers","authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Methods","GET,POST,PUT,DELETE,OPTIONS"));
        mvc.perform(options("/api/admin/products").header("Origin","https://other.example").header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
    }
}
