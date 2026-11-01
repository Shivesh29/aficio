package com.aficio.message;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageThreadRepository extends JpaRepository<MessageThread, Long> {

    Optional<MessageThread> findByFirstUserIdAndSecondUserId(Long firstUserId, Long secondUserId);
}