package com.aficio.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "music_list_item", uniqueConstraints = @UniqueConstraint(columnNames = {"list_id", "album_id"}))
@Getter
@NoArgsConstructor
public class MusicListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id")
    private MusicList list;

    @Column(name = "album_id")
    private Long albumId;

    private Integer position;

    public MusicListItem(MusicList list, Long albumId, Integer position) {
        this.list = list;
        this.albumId = albumId;
        this.position = position;
    }
}