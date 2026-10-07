package com.projectsa.employee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByRoleOrderByName(Role role);

    boolean existsByRole(Role role);

    /** UC04 engineer list: every Tech Engineer with their skills loaded. */
    @Query("""
            select e from Employee e left join fetch e.skills
            where e.role = com.projectsa.employee.Role.TECH
            order by e.name, e.id""")
    List<Employee> findTechEngineersWithSkills();

    /** UC04 skill filter choices: each skill once, ignoring upper/lower case. */
    @Query("select min(s.skill) from EngineerSkill s group by lower(s.skill) order by lower(s.skill)")
    List<String> findSkillNames();

    /** One employee with their skills loaded (F4 profile page). */
    @Query("select e from Employee e left join fetch e.skills where e.id = :id")
    Optional<Employee> findWithSkills(Long id);

    /** {@code email} must already be lower-case (see {@link Emails#normalize}). */
    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);
}
