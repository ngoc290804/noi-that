package vn.luma.api.auth;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class AccountModels {
    private AccountModels() {}
    public record Account(UUID id,String username,String displayName,String role,boolean active,
        boolean mustChangePassword,long version,Instant createdAt,Instant lastLoginAt) {}
    public record AccountInput(
        @NotBlank @Size(max=100) String displayName,
        @NotNull @Pattern(regexp="ADMIN|EDITOR") String role,
        boolean active,@NotNull @Min(0) Long version) {}
    public record CreateAccountInput(
        @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9._-]{2,63}") String username,
        @NotBlank @Size(max=100) String displayName,
        @NotNull @Pattern(regexp="ADMIN|EDITOR") String role,
        @NotNull @Size(min=6,max=72) String password) {}
    public record PasswordInput(@NotNull @Size(min=6,max=72) String password,@NotNull @Min(0) Long version) {}
    public record ChangePasswordInput(@NotBlank @Size(max=72) String currentPassword,
        @NotNull @Size(min=6,max=72) String newPassword) {}
    public record SetupInput(@NotBlank @Size(max=100) String displayName,@NotNull @Size(min=6,max=72) String password) {}
    public record SessionInfo(boolean authenticated,Account user,boolean setupRequired,boolean setupAuthorized) {}
}
