package com.projectsa.testschedule;

import com.projectsa.project.ProjectStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TestScheduleRepository extends JpaRepository<TestSchedule, Long> {

    /** Every schedule of a project, latest test first (UC03). */
    @Query("""
            select t from TestSchedule t join fetch t.createdBy
            where t.project.id = :projectId
            order by t.testAt desc, t.id desc""")
    List<TestSchedule> findForProject(Long projectId);

    /** The latest customer test of a project, or null if none was set (UC06: "customer test is finished"). */
    @Query("select max(t.testAt) from TestSchedule t where t.project.id = :projectId")
    LocalDateTime findLatestTestAt(Long projectId);

    /** The latest customer test of each of these projects that has one, as rows of {projectId, testAt} (F3). */
    @Query("""
            select t.project.id, max(t.testAt) from TestSchedule t
            where t.project.id in :projectIds
            group by t.project.id""")
    List<Object[]> findLatestTestAts(Collection<Long> projectIds);

    /** Tests still to come on projects the engineer is assigned to, soonest first (TECH dashboard). */
    @Query("""
            select t from TestSchedule t join fetch t.project p
            where t.testAt > :now and p.status = :status
              and exists (select 1 from ProjectEngineer pe
                          where pe.id.projectId = p.id and pe.id.employeeId = :employeeId)
            order by t.testAt, t.id""")
    List<TestSchedule> findUpcomingForEngineer(Long employeeId, LocalDateTime now, ProjectStatus status);
}
