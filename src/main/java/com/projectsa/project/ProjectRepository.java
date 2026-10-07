package com.projectsa.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findBySaleIdOrderByCreatedAtDesc(Long saleId);

    List<Project> findByPresaleIdOrderByCreatedAtDesc(Long presaleId);

    List<Project> findByPmIdOrderByCreatedAtDesc(Long pmId);

    /** Oldest first, so the project that has waited longest is on top. */
    List<Project> findByStatusOrderByCreatedAtAsc(ProjectStatus status);

    /**
     * F3 search, newest first. Every filter is optional (null = not used) and they are combined with AND.
     *
     * @param text   lower-case LIKE pattern for project name or customer name, escaped with '!'
     * @param id     a project ID typed in the search box (matched exactly, as an alternative to {@code text})
     * @param mineId only projects where this employee is Sale, Presale or PM in charge, or an assigned engineer
     */
    @Query(value = """
            select p from Project p join fetch p.sale s join fetch p.presale ps left join fetch p.pm pm
            where (:status is null or p.status = :status)
              and (:saleId is null or s.id = :saleId)
              and (:text is null or lower(p.projectName) like :text escape '!'
                   or lower(p.customerName) like :text escape '!' or p.id = :id)
              and (:mineId is null or s.id = :mineId or ps.id = :mineId or pm.id = :mineId
                   or exists (select 1 from ProjectEngineer pe
                              where pe.id.projectId = p.id and pe.id.employeeId = :mineId))
            order by p.createdAt desc, p.id desc""",
            countQuery = """
            select count(p) from Project p left join p.pm pm
            where (:status is null or p.status = :status)
              and (:saleId is null or p.sale.id = :saleId)
              and (:text is null or lower(p.projectName) like :text escape '!'
                   or lower(p.customerName) like :text escape '!' or p.id = :id)
              and (:mineId is null or p.sale.id = :mineId or p.presale.id = :mineId or pm.id = :mineId
                   or exists (select 1 from ProjectEngineer pe
                              where pe.id.projectId = p.id and pe.id.employeeId = :mineId))""")
    Page<Project> search(ProjectStatus status, Long saleId, String text, Long id, Long mineId, Pageable pageable);

    /** One project with everyone in charge loaded (for detail pages). */
    @Query("select p from Project p join fetch p.sale join fetch p.presale left join fetch p.pm where p.id = :id")
    Optional<Project> findDetailById(Long id);
}
