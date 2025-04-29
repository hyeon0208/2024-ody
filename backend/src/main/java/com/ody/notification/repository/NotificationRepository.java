package com.ody.notification.repository;

import com.ody.notification.domain.Notification;
import com.ody.notification.domain.NotificationStatus;
import com.ody.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select noti
            from Notification noti
            left join fetch noti.mate m
            left join m.meeting meet
            where meet.id = :meetingId and noti.sendAt < :dateTime and noti.status != "DISMISSED"
            order by noti.sendAt asc
            """)
    List<Notification> findAllByMeetingIdAndSentAtBeforeDateTimeAndStatusIsNotDismissed(
            Long meetingId,
            LocalDateTime dateTime
    );

    @Query("""
            select noti
            from Notification noti
            join fetch noti.mate m
            join fetch m.meeting
            where noti.type = :type and noti.status = :status
            """)
    List<Notification> findAllByTypeAndStatus(NotificationType type, NotificationStatus status);

    @Query("""
            select noti from Notification noti
            join fetch noti.mate m
            join fetch m.meeting
            where noti.type = :type and noti.status = :status and noti.sendAt > :dateTime
        """)
    List<Notification> findNotPassedNotificationsByTypeAndStatusAndDateTime(
            NotificationType type,
            NotificationStatus status,
            LocalDateTime dateTime
    );

    @Query("""
            select noti
            from Notification noti
            join fetch noti.mate mate
            join fetch mate.member
            where mate.meeting.id = :meetingId
            and noti.type = :type
            """)
    List<Notification> findAllMeetingIdAndType(Long meetingId, NotificationType type);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.status = 'DISMISSED' where n.mate.id = :mateId and n.sendAt > :dateTime")
    void updateAllStatusToDismissedByMateIdAndSendAtAfterDateTime(long mateId, LocalDateTime dateTime);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.status = 'DISMISSED' WHERE n.type = 'DEPARTURE_REMINDER' AND n.status = 'PENDING' AND n.sendAt <= :dateTime")
    int updateAllPassedDepartureReminderStatusToDismissedByDateTime(LocalDateTime dateTime);
}
