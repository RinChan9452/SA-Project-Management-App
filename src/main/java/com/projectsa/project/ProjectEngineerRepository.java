package com.projectsa.project;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectEngineerRepository extends JpaRepository<ProjectEngineer, ProjectEngineer.Key> {

    /** Assigned engineers with their skills, for display (UC03, UC04). */
    @Query("""
            select pe from ProjectEngineer pe
            join fetch pe.employee e left join fetch e.skills join fetch pe.assignedBy
            where pe.id.projectId = :projectId
            order by e.name""")
    List<ProjectEngineer> findForProject(Long projectId);

    @Query("select pe.id.employeeId from ProjectEngineer pe where pe.id.projectId = :projectId")
    Set<Long> findEngineerIds(Long projectId);

    /** Which of these projects the engineer is assigned to (F3 row buttons). */
    @Query("""
            select pe.id.projectId from ProjectEngineer pe
            where pe.id.employeeId = :employeeId and pe.id.projectId in :projectIds""")
    Set<Long> findAssignedProjectIds(Long employeeId, Collection<Long> projectIds);

    /** Projects an engineer is assigned to, newest first (TECH dashboard). */
    @Query("""
            select p from ProjectEngineer pe join pe.project p
            where pe.id.employeeId = :employeeId
            order by p.createdAt desc, p.id desc""")
    List<Project> findProjectsOfEngineer(Long employeeId);
}
