package vn.luma.api.auth;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="app.media.upload-token=auth-setup-token-at-least-32-characters")
@EnabledIfEnvironmentVariable(named="LUMA_AUTH_HTTP_TESTS",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthFlowIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    // Six-character test-only passwords exercise every password-writing endpoint.
    private final String initial="Test1!";
    private final String replacement="Test2!";
    private Browser admin;
    private String editorId;
    private final class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf;
        HttpResponse<String> call(String path,String method,Object body,boolean protection)throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(20));
            if(protection){if(csrf==null)csrf=json.readTree(call("/api/auth/csrf","GET",null,false).body()).get("token").asText();builder.header("X-CSRF-TOKEN",csrf);}
            String value=body==null?"":body instanceof String?(String)body:json.writeValueAsString(body);
            if(body!=null)builder.header("Content-Type",body instanceof String?"application/x-www-form-urlencoded":"application/json");
            return client.send(builder.method(method,method.equals("GET")?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(value)).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode tree(String path)throws Exception{var response=call(path,"GET",null,false);assertThat(response.statusCode()).isEqualTo(200);return json.readTree(response.body());}
        HttpResponse<String> login(String username,String password)throws Exception{var result=call("/api/auth/login","POST","username="+URLEncoder.encode(username,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8),true);csrf=null;return result;}
    }
    @Test @Order(1) void ownerCreatesFirstAdminWithoutDefaultPassword()throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM luma.admin_accounts",Integer.class)).isZero();
        admin=new Browser();assertThat(admin.tree("/api/auth/session").get("setupRequired").asBoolean()).isTrue();
        assertThat(admin.call("/api/auth/setup","POST",Map.of("displayName","Quản trị viên","password",initial),true).statusCode()).isEqualTo(403);
        admin.csrf=json.readTree(admin.call("/api/auth/csrf","GET",null,false).body()).get("token").asText();
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/setup-session")).header("Authorization","Bearer auth-setup-token-at-least-32-characters").header("X-CSRF-TOKEN",admin.csrf).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(admin.client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
        assertThat(admin.call("/api/auth/setup","POST",Map.of("displayName","Quản trị viên","password","Ab1!x"),true).statusCode()).isEqualTo(400);
        assertThat(admin.call("/api/auth/setup","POST",Map.of("displayName","Quản trị viên","password",initial),true).statusCode()).isEqualTo(201);
        String hash=jdbc.queryForObject("SELECT password_hash FROM luma.admin_accounts WHERE username='admin'",String.class);
        assertThat(hash).startsWith("$2a$12$").doesNotContain(initial);
        assertThat(admin.login("admin",initial).statusCode()).isEqualTo(200);
        assertThat(admin.tree("/api/auth/session").get("user").get("role").asText()).isEqualTo("ADMIN");
        assertThat(admin.call("/api/admin/catalog","GET",null,false).statusCode()).isEqualTo(200);
    }
    @Test @Order(2) void sessionIsPersistentAndLegacyBearerAndCsrfBypassAreRejected()throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM luma.spring_session WHERE principal_name='admin'",Integer.class)).isPositive();
        var anonymous=new Browser();
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/catalog")).header("Authorization","Bearer auth-setup-token-at-least-32-characters").GET().build();
        assertThat(anonymous.client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(admin.call("/api/admin/accounts","POST",Map.of(),false).statusCode()).isEqualTo(403);
        assertThat(anonymous.login("admin","wrong-password").statusCode()).isEqualTo(401);
        assertThat(admin.tree("/api/auth/session").get("authenticated").asBoolean()).isTrue();
        var cookie=admin.call("/api/auth/csrf","GET",null,false);
        assertThat(admin.cookies.getCookieStore().getCookies()).anyMatch(c->c.getName().equals("LUMA_SESSION")&&c.isHttpOnly());
    }
    @Test @Order(3) void adminManagesAccountsAndCannotDisableOrDeleteSelf()throws Exception {
        assertThat(admin.call("/api/admin/accounts","POST",Map.of("username","too-short","displayName","Test","role","EDITOR","password","Ab1!x"),true).statusCode()).isEqualTo(400);
        var created=admin.call("/api/admin/accounts","POST",Map.of("username","editor-test","displayName","Biên tập viên","role","EDITOR","password",initial),true);
        assertThat(created.statusCode()).isEqualTo(201);var account=json.readTree(created.body());editorId=account.get("id").asText();
        assertThat(account.has("passwordHash")).isFalse();assertThat(account.has("password")).isFalse();
        var own=admin.tree("/api/auth/session").get("user");
        assertThat(admin.call("/api/admin/accounts/"+own.get("id").asText(),"PUT",Map.of("displayName","Admin","role","EDITOR","active",false,"version",own.get("version").asLong()),true).statusCode()).isEqualTo(409);
        assertThat(admin.call("/api/admin/accounts/"+own.get("id").asText()+"?version="+own.get("version").asLong(),"DELETE",null,true).statusCode()).isEqualTo(409);
        assertThat(admin.call("/api/admin/accounts/"+editorId,"PUT",Map.of("displayName","Tên mới","role","EDITOR","active",true,"version",99),true).statusCode()).isEqualTo(409);
    }
    @Test @Order(4) void employeeMustChangePasswordAndCannotManageAccounts()throws Exception {
        var editor=new Browser();assertThat(editor.login("editor-test",initial).statusCode()).isEqualTo(200);
        assertThat(editor.call("/api/admin/catalog","GET",null,false).statusCode()).isEqualTo(403);
        assertThat(editor.call("/api/auth/password","POST",Map.of("currentPassword",initial,"newPassword","Ab1!x"),true).statusCode()).isEqualTo(400);
        assertThat(editor.call("/api/auth/password","POST",Map.of("currentPassword",initial,"newPassword",replacement),true).statusCode()).isEqualTo(204);
        assertThat(editor.call("/api/admin/catalog","GET",null,false).statusCode()).isEqualTo(401);
        editor.csrf=null;assertThat(editor.login("editor-test",replacement).statusCode()).isEqualTo(200);
        assertThat(editor.call("/api/admin/catalog","GET",null,false).statusCode()).isEqualTo(200);
        assertThat(editor.call("/api/admin/accounts","GET",null,false).statusCode()).isEqualTo(403);
        assertThat(editor.call("/api/admin/accounts","POST",Map.of("username","intruder","displayName","X","role","ADMIN","password",initial),true).statusCode()).isEqualTo(403);
    }
    @Test @Order(5) void resetLockAndDeleteRevokeEmployeeSessions()throws Exception {
        var editor=new Browser();assertThat(editor.login("editor-test",replacement).statusCode()).isEqualTo(200);
        JsonNode row=findEditor();
        assertThat(admin.call("/api/admin/accounts/"+editorId+"/password","PUT",Map.of("password","Ab1!x","version",row.get("version").asLong()),true).statusCode()).isEqualTo(400);
        assertThat(admin.call("/api/admin/accounts/"+editorId+"/password","PUT",Map.of("password",initial,"version",row.get("version").asLong()),true).statusCode()).isEqualTo(204);
        assertThat(editor.call("/api/admin/catalog","GET",null,false).statusCode()).isEqualTo(401);
        row=findEditor();assertThat(admin.call("/api/admin/accounts/"+editorId,"PUT",Map.of("displayName","Biên tập viên","role","EDITOR","active",false,"version",row.get("version").asLong()),true).statusCode()).isEqualTo(200);
        assertThat(new Browser().login("editor-test",initial).statusCode()).isEqualTo(401);
        row=findEditor();assertThat(admin.call("/api/admin/accounts/"+editorId+"?version="+row.get("version").asLong(),"DELETE",null,true).statusCode()).isEqualTo(204);
    }
    @Test @Order(6) void loginRateLimitAndLogoutAreEnforced()throws Exception {
        var bad=new Browser();for(int i=0;i<10;i++)assertThat(bad.login("missing-test-user","bad-password").statusCode()).isEqualTo(401);
        assertThat(bad.login("missing-test-user","bad-password").statusCode()).isEqualTo(429);
        assertThat(admin.call("/api/auth/logout","POST",null,true).statusCode()).isEqualTo(204);
        assertThat(admin.call("/api/admin/catalog","GET",null,false).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM luma.products",Integer.class)).isEqualTo(4);
    }
    private JsonNode findEditor()throws Exception {for(var node:admin.tree("/api/admin/accounts"))if(node.get("id").asText().equals(editorId))return node;throw new AssertionError("Missing employee");}
}
