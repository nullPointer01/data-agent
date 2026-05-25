package com.ai.security;
import com.ai.security.rbac.SysRole;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Loads persisted system users for Spring Security authentication.
 *
 * @author data-agent
 */
@Service
public class SysUserDetailsService implements UserDetailsService {

    private final SysUserRepository userRepository;

    public SysUserDetailsService(SysUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .authorities(user.getRoles().stream()
                        .filter(SysRole::isEnabled)
                        .map(role -> new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + role.getRoleCode()))
                        .collect(Collectors.toSet()))
                .build();
    }
}
