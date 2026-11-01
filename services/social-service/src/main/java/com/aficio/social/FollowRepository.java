package com.aficio.social;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    Optional<Follow> findByFollowerIdAndFollowingIdAndStatus(Long followerId, Long followingId, String status);

    List<Follow> findByFollowingId(Long followingId);

    List<Follow> findByFollowingIdAndStatus(Long followingId, String status);

    List<Follow> findByFollowerId(Long followerId);

    List<Follow> findByFollowerIdAndStatus(Long followerId, String status);

    long countByFollowingId(Long followingId);

    long countByFollowingIdAndStatus(Long followingId, String status);

    long countByFollowerId(Long followerId);

    long countByFollowerIdAndStatus(Long followerId, String status);
}