package com.aficio.collection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicListItemRepository extends JpaRepository<MusicListItem, Long> {

    List<MusicListItem> findByListIdOrderByPosition(Long listId);

    Optional<MusicListItem> findByListIdAndAlbumId(Long listId, Long albumId);

    void deleteByListIdAndAlbumId(Long listId, Long albumId);

    void deleteByListId(Long listId);
}
