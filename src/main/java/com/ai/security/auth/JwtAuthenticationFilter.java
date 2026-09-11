package com.ai.security.auth;
import com.ai.security.auth.JwtTokenProvider;
import com.ai.security.SecurityConstants;
import com.ai.security.SysUser;
import com.ai.security.SysUserRepository;
import com.ai.security.TenantUser;
import com.ai.security.rbac.RolePermissionService;

import com.ai.logging.RequestCorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从 Bearer JWT 中认证请求身份。
 *
 * @author data-agent
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;

    private final SysUserRepository userRepository;

    private final RolePermissionService rolePermissionService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, SysUserRepository userRepository,
            RolePermissionService rolePermissionService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.rolePermissionService = rolePermissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                String userId = jwtTokenProvider.getUserId(token);
                userRepository.findById(userId)
                        .filter(SysUser::isEnabled)
                        .ifPresent(this::setAuthentication);
            } catch (Exception e) {
                LOGGER.warn("JWT 认证失败: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(SysUser user) {
        Set<SimpleGrantedAuthority> authorities = rolePermissionService.toRoleCodes(user.getRoles()).stream()
                .map(role -> new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + role))
                .collect(Collectors.toSet());
        TenantUser principal = new TenantUser(
                user.getId(),
                user.getUsername(),
                user.getTenantId(),
                authorities);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        putAuthenticatedContext(principal);
    }

    private void putAuthenticatedContext(TenantUser principal) {
        putIfPresent(RequestCorrelationContext.MDC_USER_ID, principal.getUserId());
        putIfPresent(RequestCorrelationContext.MDC_USERNAME, principal.getUsername());
        putIfPresent(RequestCorrelationContext.MDC_TENANT_ID, principal.getTenantId());
    }

    private void putIfPresent(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(SecurityConstants.BEARER_TOKEN_PREFIX)) {
            return bearerToken.substring(SecurityConstants.BEARER_TOKEN_PREFIX.length());
        }
        return null;
    }
}
