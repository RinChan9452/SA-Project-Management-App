package com.projectsa.auth;

import com.projectsa.employee.Employee;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Replaces the logged-in {@link CurrentUser} after the employee changed their own profile (F4), so the navbar shows
 * the new name and picture straight away without logging in again.
 */
@Component
public class CurrentUserSession {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public void refresh(Employee employee, HttpServletRequest request, HttpServletResponse response) {
        CurrentUser user = new CurrentUser(employee);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }
}
