package com.projectsa.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {

    boolean existsByProjectIdAndType(Long projectId, DocumentType type);

    /** Solution + Product Requirement documents, newest first (older versions are kept). */
    @Query("""
            select d from ProjectDocument d join fetch d.uploadedBy
            where d.project.id = :projectId and d.type in (
                com.projectsa.document.DocumentType.SOLUTION,
                com.projectsa.document.DocumentType.PRODUCT_REQUIREMENT)
            order by d.uploadedAt desc, d.id desc""")
    List<ProjectDocument> findSolutionDocuments(Long projectId);

    /** UC03 step 3 (§5b): Solution + Product Requirement documents and attachments of APPROVED requirements. */
    @Query("""
            select d from ProjectDocument d join fetch d.uploadedBy left join fetch d.requirement r
            where d.project.id = :projectId
              and (d.type in (com.projectsa.document.DocumentType.SOLUTION,
                              com.projectsa.document.DocumentType.PRODUCT_REQUIREMENT)
                   or r.status = com.projectsa.requirement.RequirementStatus.APPROVED)
            order by d.uploadedAt desc, d.id desc""")
    List<ProjectDocument> findPublicDocuments(Long projectId);

    /** Every requirement attachment of a project, whatever the requirement's status. */
    @Query("""
            select d from ProjectDocument d join fetch d.requirement
            where d.project.id = :projectId
              and d.type = com.projectsa.document.DocumentType.REQUIREMENT_ATTACHMENT
            order by d.id""")
    List<ProjectDocument> findAttachments(Long projectId);

    /** One document with what the download rule needs. */
    @Query("select d from ProjectDocument d join fetch d.project left join fetch d.requirement where d.id = :id")
    Optional<ProjectDocument> findForDownload(Long id);
}
