package com.carpool.web.security;

import com.carpool.domain.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal stored in the SecurityContext after JWT validation.
 * Controllers access this via @AuthenticationPrincipal AuthenticatedUser user.
 *
 * We store userId and telegramId here to avoid DB lookups
 * in controllers for simple identity checks.
 */
@Getter
public class AuthenticatedUser implements UserDetails {

    private final Long   userId;
    private final Long   telegramId;
    private final String role;

    public AuthenticatedUser(Long userId, Long telegramId, String role) {
        this.userId     = userId;
        this.telegramId = telegramId;
        this.role       = role;
    }

    public UserRole getUserRole() {
        return UserRole.valueOf(role);
    }

    public boolean canDrive() {
        UserRole r = getUserRole();
        return r == UserRole.DRIVER || r == UserRole.BOTH || r == UserRole.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    // JWT-authenticated users have no stored password in our system
    @Override public String getPassword()  { return null; }
    @Override public String getUsername()  { return String.valueOf(userId); }
    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()              { return true; }
}
