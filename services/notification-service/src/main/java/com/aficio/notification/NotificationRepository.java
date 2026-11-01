package com.aficio.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndReadOrderByCreatedAtDesc(Long userId, boolean read);

    long countByUserIdAndRead(Long userId, boolean read);
}