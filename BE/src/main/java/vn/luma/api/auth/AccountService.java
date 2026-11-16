package vn.luma.api.auth;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static vn.luma.api.auth.AccountModels.*;

@Service
@Transactional
public class AccountService implements UserDetailsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AccountService(NamedParameterJdbcTemplate jdbc,PasswordEncoder passwords){this.jdbc=jdbc;this.passwords=passwords;}
    @Override @Transactional(readOnly=true)
    public UserDetails loadUserByUsername(String username){
        var rows=jdbc.query("SELECT username,password_hash,role,active FROM luma.admin_accounts WHERE username=:username AND deleted_at IS NULL",Map.of("username",username.toLowerCase(Locale.ROOT)),(rs,n)->
            User.withUsername(rs.getString("username")).password(rs.getString("password_hash")).roles(rs.getString("role")).disabled(!rs.getBoolean("active")).build());
        if(rows.isEmpty())throw new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ.");
        return rows.get(0);
    }
    @Transactional(readOnly=true) public boolean setupRequired(){return jdbc.queryForObject("SELECT count(*) FROM luma.admin_accounts",Map.of(),Long.class)==0;}
    @Transactional(readOnly=true) public List<Account> list(){return jdbc.query("SELECT * FROM luma.admin_accounts WHERE deleted_at IS NULL ORDER BY created_at,id",(rs,n)->map(rs));}
    @Transactional(readOnly=true) public Account byUsername(String username){return jdbc.query("SELECT * FROM luma.admin_accounts WHERE username=:username AND deleted_at IS NULL",Map.of("username",username),(rs,n)->map(rs)).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Phiên đăng nhập không còn hợp lệ."));}
    public Account setup(SetupInput input){
        lock();if(!setupRequired())throw conflict("Tài khoản quản trị đã được khởi tạo.");
        return createInternal(new CreateAccountInput("admin",input.displayName(),"ADMIN",input.password()),false);
    }
    public Account create(CreateAccountInput input,String actor){lock();requireAdmin(actor);return createInternal(input,true);}
    private Account createInternal(CreateAccountInput input,boolean mustChange){
        validatePassword(input.password());UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO luma.admin_accounts(id,username,display_name,password_hash,role,must_change_password) VALUES(:id,:username,:name,:password,:role,:mustChange)",Map.of("id",id,"username",input.username(),"name",input.displayName().strip(),"password",passwords.encode(input.password()),"role",input.role(),"mustChange",mustChange));
        return get(id);
    }
    public Account update(UUID id,AccountInput input,String actor){
        lock();requireAdmin(actor);var existing=get(id);
        if(existing.username().equals(actor)&&(!input.active()||!input.role().equals("ADMIN")))throw conflict("Không thể tự khóa hoặc hạ quyền tài khoản đang đăng nhập.");
        if(existing.active()&&existing.role().equals("ADMIN")&&(!input.active()||!input.role().equals("ADMIN")))requireAnotherAdmin(id);
        int changed=jdbc.update("UPDATE luma.admin_accounts SET display_name=:name,role=:role,active=:active WHERE id=:id AND version=:version AND deleted_at IS NULL",Map.of("id",id,"version",input.version(),"name",input.displayName().strip(),"role",input.role(),"active",input.active()));
        changed(changed);
        if(existing.active()!=input.active()||!existing.role().equals(input.role()))revokeSessions(existing.username());
        return get(id);
    }
    public void delete(UUID id,long version,String actor){
        lock();requireAdmin(actor);var existing=get(id);
        if(existing.username().equals(actor))throw conflict("Không thể xóa tài khoản đang đăng nhập.");
        if(existing.active()&&existing.role().equals("ADMIN"))requireAnotherAdmin(id);
        changed(jdbc.update("UPDATE luma.admin_accounts SET deleted_at=clock_timestamp(),active=false WHERE id=:id AND version=:version AND deleted_at IS NULL",Map.of("id",id,"version",version)));
        revokeSessions(existing.username());
    }
    public void resetPassword(UUID id,PasswordInput input,String actor){
        lock();requireAdmin(actor);var account=get(id);
        if(account.username().equals(actor))throw conflict("Dùng mục Đổi mật khẩu để đổi mật khẩu của chính bạn.");
        validatePassword(input.password());
        changed(jdbc.update("UPDATE luma.admin_accounts SET password_hash=:password,must_change_password=true WHERE id=:id AND version=:version AND deleted_at IS NULL",Map.of("id",id,"version",input.version(),"password",passwords.encode(input.password()))));
        revokeSessions(account.username());
    }
    public void changePassword(ChangePasswordInput input,String actor){
        lock();var details=loadUserByUsername(actor);
        if(!details.isEnabled()||!passwords.matches(input.currentPassword(),details.getPassword()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu hiện tại chưa đúng.");
        validatePassword(input.newPassword());
        if(passwords.matches(input.newPassword(),details.getPassword()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu mới phải khác mật khẩu hiện tại.");
        jdbc.update("UPDATE luma.admin_accounts SET password_hash=:password,must_change_password=false WHERE username=:username AND deleted_at IS NULL",Map.of("username",actor,"password",passwords.encode(input.newPassword())));
        revokeSessions(actor);
    }
    public void markLogin(String username){jdbc.update("UPDATE luma.admin_accounts SET last_login_at=clock_timestamp() WHERE username=:username AND deleted_at IS NULL",Map.of("username",username));}
    public void requireAdmin(String username){var actor=byUsername(username);if(!actor.active()||!actor.role().equals("ADMIN")||actor.mustChangePassword())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ quản trị viên có quyền quản lý tài khoản.");}
    public void revokeSessions(String username){jdbc.update("DELETE FROM luma.spring_session WHERE principal_name=:username",Map.of("username",username));}
    private void lock(){jdbc.query("SELECT pg_advisory_xact_lock(1886745198)",(rs,n)->0);}
    private void requireAnotherAdmin(UUID id){Long others=jdbc.queryForObject("SELECT count(*) FROM luma.admin_accounts WHERE id<>:id AND active AND role='ADMIN' AND deleted_at IS NULL",Map.of("id",id),Long.class);if(others==null||others==0)throw conflict("Cần giữ ít nhất một quản trị viên đang hoạt động.");}
    private Account get(UUID id){return jdbc.query("SELECT * FROM luma.admin_accounts WHERE id=:id AND deleted_at IS NULL",Map.of("id",id),(rs,n)->map(rs)).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản."));}
    private static void validatePassword(String password){if(password==null||password.length()<6||password.getBytes(StandardCharsets.UTF_8).length>72||password.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu cần ít nhất 6 ký tự và tối đa 72 byte UTF-8.");}
    private static void changed(int count){if(count==0)throw conflict("Tài khoản đã thay đổi ở phiên khác. Tải lại danh sách trước khi tiếp tục.");}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static Account map(java.sql.ResultSet rs)throws java.sql.SQLException {var last=rs.getTimestamp("last_login_at");return new Account(rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("display_name"),rs.getString("role"),rs.getBoolean("active"),rs.getBoolean("must_change_password"),rs.getLong("version"),rs.getTimestamp("created_at").toInstant(),last==null?null:last.toInstant());}
}
