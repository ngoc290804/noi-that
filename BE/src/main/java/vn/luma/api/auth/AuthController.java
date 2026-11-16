package vn.luma.api.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static vn.luma.api.auth.AccountModels.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final String setupToken;
    private static final String SETUP="luma.initial-admin-authorized-until";
    public AuthController(AccountService accounts,@Value("${app.media.upload-token:}") String setupToken){this.accounts=accounts;this.setupToken=setupToken;}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/session") public SessionInfo session(Authentication auth,HttpServletRequest request){
        var account=auth==null||auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken?null:accounts.byUsername(auth.getName());
        return new SessionInfo(account!=null&&account.active(),account,accounts.setupRequired(),setupAuthorized(request));
    }
    @PostMapping("/setup-session") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void authorizeSetup(@RequestHeader(value="Authorization",required=false) String authorization,HttpServletRequest request){
        if(!accounts.setupRequired())throw new ResponseStatusException(HttpStatus.CONFLICT,"Tài khoản admin đã tồn tại. Hãy đăng nhập.");
        if(setupToken.length()<32||authorization==null||!MessageDigest.isEqual(("Bearer "+setupToken).getBytes(StandardCharsets.UTF_8),authorization.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Mã khởi tạo chưa đúng.");
        request.getSession(true).setAttribute(SETUP,System.currentTimeMillis()+10*60*1000L);
    }
    @PostMapping("/setup") @ResponseStatus(HttpStatus.CREATED)
    public Account setup(@RequestBody @Valid SetupInput input,HttpServletRequest request){
        if(!setupAuthorized(request))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Xác nhận quyền khởi tạo trước khi tạo admin.");
        var result=accounts.setup(input);
        request.getSession().removeAttribute(SETUP);
        return result;
    }
    @PostMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody @Valid ChangePasswordInput input,Authentication auth,HttpServletRequest request){
        accounts.changePassword(input,auth.getName());
        var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
    }
    private static boolean setupAuthorized(HttpServletRequest request){var session=request.getSession(false);if(session==null)return false;var until=session.getAttribute(SETUP);return until instanceof Long value&&value>System.currentTimeMillis();}
}
