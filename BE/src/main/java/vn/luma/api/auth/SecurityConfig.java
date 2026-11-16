package vn.luma.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,AccountService accounts,LoginAttemptService attempts,ObjectMapper json)throws Exception {
        http.cors(Customizer.withDefaults()).csrf(Customizer.withDefaults())
            .requestCache(cache->cache.requestCache(new NullRequestCache()))
            .authorizeHttpRequests(auth->auth
                .requestMatchers("/api/auth/password").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/accounts/**","/api/admin/accounts").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN","EDITOR")
                .anyRequest().permitAll())
            .exceptionHandling(errors->errors
                .authenticationEntryPoint((request,response,error)->problem(json,response,401,"Cần đăng nhập để tiếp tục.","AUTH_REQUIRED"))
                .accessDeniedHandler((request,response,error)->problem(json,response,403,error instanceof CsrfException?"Phiên xác nhận đã hết hạn. Tải lại trang và thử lại.":"Bạn không có quyền thực hiện thao tác này.",error instanceof CsrfException?"CSRF_INVALID":"FORBIDDEN")))
            .formLogin(form->form.loginProcessingUrl("/api/auth/login")
                .successHandler((request,response,auth)->{
                    attempts.succeeded(auth.getName(),request.getRemoteAddr());accounts.markLogin(auth.getName());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.setCharacterEncoding("UTF-8");
                    json.writeValue(response.getWriter(),new AccountModels.SessionInfo(true,accounts.byUsername(auth.getName()),false,false));
                })
                .failureHandler((request,response,error)->problem(json,response,401,"Tên đăng nhập hoặc mật khẩu chưa đúng, hoặc tài khoản đã bị khóa.","LOGIN_FAILED")))
            .logout(logout->logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("LUMA_SESSION")
                .logoutSuccessHandler((request,response,auth)->response.setStatus(204)).permitAll())
            .sessionManagement(session->session.sessionFixation(fixation->fixation.changeSessionId()));
        http.addFilterBefore(new OncePerRequestFilter(){
            @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getMethod().equals("POST")||!request.getRequestURI().substring(request.getContextPath().length()).equals("/api/auth/login");}
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
                String username=request.getParameter("username");
                if(username==null||username.length()>64||!attempts.allow(username,request.getRemoteAddr())){problem(json,response,429,"Đã thử đăng nhập nhiều lần. Vui lòng thử lại sau 15 phút.","LOGIN_RATE_LIMIT");return;}
                chain.doFilter(request,response);
            }
        },UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(new OncePerRequestFilter(){
            @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/admin/");}
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
                var auth=SecurityContextHolder.getContext().getAuthentication();
                if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)){
                    try {
                        var user=accounts.byUsername(auth.getName());
                        if(user==null||!user.active()||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_"+user.role()))){
                            var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();problem(json,response,401,"Tài khoản hoặc quyền truy cập đã thay đổi. Đăng nhập lại.","AUTH_REQUIRED");return;
                        }
                        if(user.mustChangePassword()){problem(json,response,403,"Bạn cần đổi mật khẩu trước khi quản lý dữ liệu.","PASSWORD_CHANGE_REQUIRED");return;}
                    } catch(org.springframework.web.server.ResponseStatusException exception){problem(json,response,401,"Phiên đăng nhập không còn hợp lệ.","AUTH_REQUIRED");return;}
                }
                chain.doFilter(request,response);
            }
        },SecurityContextHolderFilter.class);
        return http.build();
    }
    private static void problem(ObjectMapper json,HttpServletResponse response,int status,String detail,String code)throws IOException {
        response.setStatus(status);response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(),Map.of("status",status,"detail",detail,"code",code));
    }
}
