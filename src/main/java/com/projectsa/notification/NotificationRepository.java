package com.projectsa.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every F2 query is limited to one recipient and to notifications already sent ({@code send_at <= now}). */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /** Bell badge: my unread notifications that are already sent. */
    long countByRecipientIdAndReadAtIsNullAndSendAtLessThanEqual(Long recipientId, LocalDateTime now);

    /** F2 list: my sent notifications, newest first. */
    @Query(value = """
            select n from Notification n left join fetch n.project
            where n.recipient.id = :me and n.sendAt <= :now
            order by n.sendAt desc, n.id desc""",
            countQuery = "select count(n) from Notification n where n.recipient.id = :me and n.sendAt <= :now")
    Page<Notification> inbox(@Param("me") Long recipientId, @Param("now") LocalDateTime now, Pageable pageable);

    /** F1: my latest unread notifications. */
    @Query("""
            select n from Notification n left join fetch n.project
            where n.recipient.id = :me and n.readAt is null and n.sendAt <= :now
            order by n.sendAt desc, n.id desc""")
    List<Notification> latestUnread(@Param("me") Long recipientId, @Param("now") LocalDateTime now, Limit limit);

    /** Opening one: only my own, and only once it is sent. */
    @Query("select n from Notification n where n.id = :id and n.recipient.id = :me and n.sendAt <= :now")
    Optional<Notification> findMine(@Param("id") Long id, @Param("me") Long recipientId,
                                    @Param("now") LocalDateTime now);

    /** Mark all as read: my unread, sent notifications. Returns how many were marked. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipient.id = :me and n.readAt is null and n.sendAt <= :now""")
    int markAllRead(@Param("me") Long recipientId, @Param("now") LocalDateTime now);
}
