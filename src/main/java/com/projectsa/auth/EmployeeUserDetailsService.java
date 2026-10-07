package com.projectsa.auth;

import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Emails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employees;

    public EmployeeUserDetailsService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return employees.findByEmail(Emails.normalize(email))
                .map(CurrentUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No employee with that email"));
    }
}
