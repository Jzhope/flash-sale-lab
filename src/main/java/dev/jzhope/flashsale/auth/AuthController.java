package dev.jzhope.flashsale.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    public record Credentials(@Pattern(regexp="[a-z0-9_]{3,32}") @jakarta.validation.constraints.NotNull String username,
                              @jakarta.validation.constraints.NotNull @Size(min=8, max=64) @Pattern(regexp="[\\x20-\\x7E]+") String password) {}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthService.Token register(@Valid @RequestBody Credentials credentials) { return auth.register(credentials.username(), credentials.password()); }
    @PostMapping("/login")
    public AuthService.Token login(@Valid @RequestBody Credentials credentials) { return auth.login(credentials.username(), credentials.password()); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) { auth.logout(authorization.substring(7)); }
}
