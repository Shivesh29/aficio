package com.aficio.message;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    List<DirectMessage> findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderBySentAtAsc(
            Long senderId,
            Long recipientId,
            Long reverseSenderId,
            Long reverseRecipientId
    );

    List<DirectMessage> findBySenderIdOrRecipientIdOrderBySentAtDesc(
            Long senderId,
            Long recipientId
    );

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    long countByRecipientIdAndSenderIdAndIsReadFalse(Long recipientId, Long senderId);

    @Modifying
    @Transactional
    @Query("UPDATE DirectMessage m SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP WHERE m.recipientId = :recipientId AND m.senderId = :senderId AND m.isRead = false")
    int markConversationAsRead(
            @Param("recipientId") Long recipientId,
            @Param("senderId") Long senderId
    );
}
