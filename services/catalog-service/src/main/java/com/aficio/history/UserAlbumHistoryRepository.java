package com.aficio.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAlbumHistoryRepository extends JpaRepository<UserAlbumHistory, Long> {

    List<UserAlbumHistory> findByUsernameIgnoreCaseOrderByPlayedAtDesc(String username);

    List<UserAlbumHistory> findByUsernameIgnoreCaseAndSyncedTrueOrderByPlayedAtDesc(String username);

    Optional<UserAlbumHistory> findFirstByUsernameIgnoreCaseAndAlbumIdOrderByIdDesc(String username, Long albumId);

    Optional<UserAlbumHistory> findByUsernameIgnoreCaseAndAlbumId(String username, Long albumId);

    boolean existsByUsernameIgnoreCaseAndAlbumId(String username, Long albumId);
}
