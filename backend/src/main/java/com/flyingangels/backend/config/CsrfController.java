package com.flyingangels.backend.config;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    public CsrfToken token(CsrfToken token) { return token; }
}
