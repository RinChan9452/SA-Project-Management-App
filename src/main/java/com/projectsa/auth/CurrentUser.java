package com.projectsa.auth;

import com.projectsa.employee.Employee;
import com.projectsa.employee.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The logged-in employee. Authority is {@code ROLE_<role>}, e.g. ROLE_SALE. */
public class CurrentUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final Role role;
    private final String profilePicturePath;

    public CurrentUser(Employee employee) {
        this.id = employee.getId();
        this.email = employee.getEmail();
        this.passwordHash = employee.getPasswordHash();
        this.name = employee.getName();
        this.role = employee.getRole();
        this.profilePicturePath = employee.getProfilePicturePath();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    /** For the navbar avatar (same property name as on {@link Employee}, so the avatar fragment takes both). */
    public String getProfilePicturePath() {
        return profilePicturePath;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
