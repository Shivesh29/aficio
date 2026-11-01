package com.aficio.collection;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicListRepository extends JpaRepository<MusicList, Long> {

    List<MusicList> findByOwnerId(Long ownerId);

    List<MusicList> findByOwnerIdAndIsPublicTrue(Long ownerId);

    List<MusicList> findByIsPublicTrue();

    long countByOwnerId(Long ownerId);
}