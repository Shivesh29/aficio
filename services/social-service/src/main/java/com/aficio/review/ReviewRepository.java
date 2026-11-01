package com.aficio.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByAlbumId(Long albumId);
    List<Review> findByUserId(Long userId);
    Optional<Review> findByUserIdAndAlbumId(Long userId, Long albumId);
    long countByUserId(Long userId);
}