package vn.luma.api.auth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static vn.luma.api.auth.AccountModels.*;
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountsController {
    private final AccountService accounts;
    public AccountsController(AccountService accounts){this.accounts=accounts;}
    @GetMapping public List<Account> list(Authentication auth){accounts.requireAdmin(auth.getName());return accounts.list();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Account create(@RequestBody @Valid CreateAccountInput input,Authentication auth){return accounts.create(input,auth.getName());}
    @PutMapping("/{id}") public Account update(@PathVariable UUID id,@RequestBody @Valid AccountInput input,Authentication auth){return accounts.update(id,input,auth.getName());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,@RequestParam @Min(0) long version,Authentication auth){accounts.delete(id,version,auth.getName());}
    @PutMapping("/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void password(@PathVariable UUID id,@RequestBody @Valid PasswordInput input,Authentication auth){accounts.resetPassword(id,input,auth.getName());}
}
