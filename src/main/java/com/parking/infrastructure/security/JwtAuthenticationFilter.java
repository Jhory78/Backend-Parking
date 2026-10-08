package com.parking.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

import static com.parking.infrastructure.utils.constants.SecurityConstants.BEARER_PREFIX;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <token>}.
 * <p>
 * The account is reloaded on every request, so a deactivated account loses
 * access immediately even if its token has not expired yet (RF-41, RF-45).
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthenticatedUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractBearerToken(request)
                .flatMap(jwtTokenService::extractValidSubject)
                .flatMap(this::loadActiveUser)
                .ifPresent(user -> authenticate(user, request));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }

    private Optional<AuthenticatedUser> loadActiveUser(String email) {
        try {
            return Optional.of(userDetailsService.loadUserByUsername(email))
                    .filter(AuthenticatedUser::isEnabled);
        } catch (UsernameNotFoundException ex) {
            log.debug("Token subject no longer exists: {}", email);
            return Optional.empty();
        }
    }

    private void authenticate(AuthenticatedUser user, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
