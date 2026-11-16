package vn.luma.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private final NamedParameterJdbcTemplate jdbc;
    public LoginAttemptService(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean allow(String username,String address){
        String key=key(username,address);
        Integer count=jdbc.queryForObject("""
            INSERT INTO luma.auth_login_attempts(attempt_key,attempts) VALUES(:key,1)
            ON CONFLICT(attempt_key) DO UPDATE SET
                attempts=CASE WHEN luma.auth_login_attempts.window_started_at<now()-interval '15 minutes' THEN 1 ELSE luma.auth_login_attempts.attempts+1 END,
                window_started_at=CASE WHEN luma.auth_login_attempts.window_started_at<now()-interval '15 minutes' THEN now() ELSE luma.auth_login_attempts.window_started_at END
            RETURNING attempts
            """,Map.of("key",key),Integer.class);
        jdbc.update("DELETE FROM luma.auth_login_attempts WHERE window_started_at<now()-interval '1 day'",Map.of());
        return count!=null&&count<=10;
    }
    public void succeeded(String username,String address){jdbc.update("DELETE FROM luma.auth_login_attempts WHERE attempt_key=:key",Map.of("key",key(username,address)));}
    private static String key(String username,String address){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((username.toLowerCase(Locale.ROOT)+"\n"+address).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
