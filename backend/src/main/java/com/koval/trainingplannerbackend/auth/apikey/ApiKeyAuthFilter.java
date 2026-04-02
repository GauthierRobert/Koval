package com.koval.trainingplannerbackend.auth.apikey;

import com.koval.trainingplannerbackend.auth.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates MCP requests using personal API keys (koval_...).
 * Runs before JwtAuthenticationFilter. If the token is not an API key,
 * the request falls through to JWT auth.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (ApiKeyService.isApiKey(token)) {
                var keyOpt = apiKeyService.validateKey(token);
                if (keyOpt.isPresent()) {
                    ApiKey apiKey = keyOpt.get();
                    var userOpt = apiKeyService.resolveUser(apiKey);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                        var authentication = new UsernamePasswordAuthenticationToken(
                                user.getId(), null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } else {
                    response.sendError(401, "Invalid API key");
                    return;
                }
            }
            // Not an API key — fall through to JWT filter
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/strava") || path.startsWith("/api/auth/google");
    }
}
