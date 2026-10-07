package com.projectsa.requirement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {

    /** Every requirement of a project, newest first (UC03, UC06 step 3.7). */
    @Query("""
            select r from Requirement r join fetch r.createdBy left join fetch r.decidedBy
            where r.project.id = :projectId
            order by r.createdAt desc, r.id desc""")
    List<Requirement> findForProject(Long projectId);

    /** Requirements a PM added, with their project, newest first (PM dashboard: "my requirements' results"). */
    @Query("""
            select r from Requirement r join fetch r.project
            where r.createdBy.id = :employeeId
            order by r.createdAt desc, r.id desc""")
    List<Requirement> findCreatedBy(Long employeeId);

    /** UC07 step 1: pending requirements of projects where this Sale is in charge, oldest first. */
    @Query("""
            select r from Requirement r join fetch r.project p join fetch r.createdBy
            where r.status = com.projectsa.requirement.RequirementStatus.PENDING and p.sale.id = :saleId
            order by r.createdAt, r.id""")
    List<Requirement> findPendingForSale(Long saleId);

    /** Pending requirements of these projects (F3 row buttons). */
    @Query("""
            select r from Requirement r
            where r.project.id in :projectIds and r.status = com.projectsa.requirement.RequirementStatus.PENDING""")
    List<Requirement> findPendingForProjects(Collection<Long> projectIds);

    /** UC07: one requirement with its project and everyone in charge loaded. */
    @Query("""
            select r from Requirement r join fetch r.createdBy left join fetch r.decidedBy
            join fetch r.project p join fetch p.sale join fetch p.presale left join fetch p.pm
            where r.id = :id""")
    Optional<Requirement> findForDecision(Long id);
}
