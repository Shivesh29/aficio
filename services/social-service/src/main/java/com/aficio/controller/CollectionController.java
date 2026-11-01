package com.aficio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aficio.config.AlbumClient;
import com.aficio.config.UserClient;
import com.aficio.dto.collection.MusicListResponse;
import com.aficio.dto.collection.MusicListItemResponse;
import com.aficio.collection.MusicList;
import com.aficio.collection.MusicListItem;
import com.aficio.collection.MusicListItemRepository;
import com.aficio.collection.MusicListRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lists")
public class CollectionController {

    private final MusicListRepository listRepository;
    private final MusicListItemRepository itemRepository;
    private final UserClient userClient;
    private final AlbumClient albumClient;
    private static final Logger log = LoggerFactory.getLogger(CollectionController.class);

    public CollectionController(
            MusicListRepository listRepository,
            MusicListItemRepository itemRepository,
            UserClient userClient,
            AlbumClient albumClient
    ) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.userClient = userClient;
        this.albumClient = albumClient;
    }

    @PostMapping
    public MusicListResponse createList(
            @Valid @RequestBody ListRequest request,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        MusicList saved = listRepository.save(new MusicList(
                userInfo.id(),
                request.title(),
                request.description(),
                request.isPublic()
        ));
        return new MusicListResponse(
                saved.getId(),
                request.title(),
                request.description(),
                request.isPublic(),
                saved.getCreatedAt(),
                new com.aficio.dto.user.UserSummaryResponse(userInfo.id(), userInfo.username())
        );
    }

    @GetMapping({"", "/public"})
    public List<MusicListResponse> publicLists() {
        return listRepository.findByIsPublicTrue()
                .stream()
                .map(list -> {
                    String username = null;
                    try {
                        com.aficio.dto.user.UserSummaryResponse owner = userClient.findById(list.getOwnerId());
                        if (owner != null) {
                            username = owner.username();
                        }
                    } catch (Exception e) {
                        log.warn("Could not fetch username for ownerId {}: {}", list.getOwnerId(), e.getMessage());
                    }
                    return new MusicListResponse(
                            list.getId(),
                            list.getTitle(),
                            list.getDescription(),
                            list.isPublic(),
                            list.getCreatedAt(),
                            new com.aficio.dto.user.UserSummaryResponse(list.getOwnerId(), username)
                    );
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{listId:[0-9]+}")
    public MusicListResponse getListById(@PathVariable Long listId, Authentication authentication) {
        MusicList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found"));

        if (!list.isPublic()) {
            com.aficio.dto.user.UserSummaryResponse currentUser = null;
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
                try {
                    currentUser = userClient.findByUsername(authentication.getName());
                } catch (Exception ignored) {}
            }
            if (currentUser == null || !list.getOwnerId().equals(currentUser.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This collection is private");
            }
        }

        String username = null;
        try {
            com.aficio.dto.user.UserSummaryResponse owner = userClient.findById(list.getOwnerId());
            if (owner != null) {
                username = owner.username();
            }
        } catch (Exception e) {
            log.warn("Could not fetch username for ownerId {}: {}", list.getOwnerId(), e.getMessage());
        }

        return new MusicListResponse(
                list.getId(),
                list.getTitle(),
                list.getDescription(),
                list.isPublic(),
                list.getCreatedAt(),
                new com.aficio.dto.user.UserSummaryResponse(list.getOwnerId(), username)
        );
    }

    @GetMapping("/mine")
    public List<MusicListResponse> myLists(Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        return listRepository.findByOwnerId(userInfo.id())
                .stream()
                .map(list -> new MusicListResponse(
                        list.getId(),
                        list.getTitle(),
                        list.getDescription(),
                        list.isPublic(),
                        list.getCreatedAt(),
                        new com.aficio.dto.user.UserSummaryResponse(userInfo.id(), userInfo.username())
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/user/{username}")
    public List<MusicListResponse> userLists(@PathVariable String username, Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse userInfo = userClient.findByUsername(username);
        if (userInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        if (authentication != null && authentication.isAuthenticated() && username.equalsIgnoreCase(authentication.getName())) {
            return listRepository.findByOwnerId(userInfo.id())
                    .stream()
                    .map(list -> new MusicListResponse(
                            list.getId(),
                            list.getTitle(),
                            list.getDescription(),
                            list.isPublic(),
                            list.getCreatedAt(),
                            new com.aficio.dto.user.UserSummaryResponse(userInfo.id(), userInfo.username())
                    ))
                    .collect(Collectors.toList());
        }

        return listRepository.findByOwnerIdAndIsPublicTrue(userInfo.id())
                .stream()
                .map(list -> new MusicListResponse(
                        list.getId(),
                        list.getTitle(),
                        list.getDescription(),
                        list.isPublic(),
                        list.getCreatedAt(),
                        new com.aficio.dto.user.UserSummaryResponse(userInfo.id(), userInfo.username())
                ))
                .collect(Collectors.toList());
    }

    @PostMapping("/{listId:[0-9]+}/albums/{albumId:[0-9]+}")
    public MusicListItemResponse addAlbum(
            @PathVariable Long listId,
            @PathVariable Long albumId,
            Authentication authentication
    ) {
        MusicList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found"));
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        if (!list.getOwnerId().equals(userInfo.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can add records to this collection");
        }
        com.aficio.dto.album.AlbumResponse albumInfo = null;
        try {
            albumInfo = albumClient.findById(albumId);
        } catch (Exception e) {
            log.warn("Could not fetch album details for {}: {}", albumId, e.getMessage());
        }
        if (albumInfo == null) {
            albumInfo = new com.aficio.dto.album.AlbumResponse(albumId, null, null, null, null);
        }

        final com.aficio.dto.album.AlbumResponse finalAlbumInfo = albumInfo;
        MusicListItem item = itemRepository.findByListIdAndAlbumId(listId, albumId)
                .orElseGet(() -> itemRepository.save(new MusicListItem(
                        list,
                        albumId,
                        itemRepository.findByListIdOrderByPosition(listId).size() + 1
                )));
        return new MusicListItemResponse(
                item.getId(),
                finalAlbumInfo,
                item.getPosition()
        );
    }

    @DeleteMapping("/{listId:[0-9]+}/albums/{albumId:[0-9]+}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAlbum(
            @PathVariable Long listId,
            @PathVariable Long albumId,
            Authentication authentication
    ) {
        MusicList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found"));
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        if (!list.getOwnerId().equals(userInfo.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can edit this collection");
        }
        itemRepository.deleteByListIdAndAlbumId(listId, albumId);
    }

    @DeleteMapping("/{listId:[0-9]+}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteList(
            @PathVariable Long listId,
            Authentication authentication
    ) {
        MusicList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found"));
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        if (!list.getOwnerId().equals(userInfo.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can delete this collection");
        }
        itemRepository.deleteByListId(listId);
        listRepository.delete(list);
    }

    @PutMapping("/{listId:[0-9]+}")
    public MusicListResponse updateList(
            @PathVariable Long listId,
            @Valid @RequestBody ListRequest request,
            Authentication authentication
    ) {
        MusicList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found"));
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        if (!list.getOwnerId().equals(userInfo.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can edit this collection");
        }
        list.setTitle(request.title());
        list.setDescription(request.description());
        list.setPublic(request.isPublic());
        MusicList saved = listRepository.save(list);
        return new MusicListResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.isPublic(),
                saved.getCreatedAt(),
                new com.aficio.dto.user.UserSummaryResponse(userInfo.id(), userInfo.username())
        );
    }

    @GetMapping("/{listId:[0-9]+}/albums")
    public List<MusicListItemResponse> albums(@PathVariable Long listId, Authentication authentication) {
        MusicList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found"));

        if (!list.isPublic()) {
            com.aficio.dto.user.UserSummaryResponse currentUser = null;
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
                try {
                    currentUser = userClient.findByUsername(authentication.getName());
                } catch (Exception ignored) {}
            }
            if (currentUser == null || !list.getOwnerId().equals(currentUser.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This collection is private");
            }
        }

        return itemRepository.findByListIdOrderByPosition(listId)
                .stream()
                .map(item -> {
                    com.aficio.dto.album.AlbumResponse albumResp = null;
                    try {
                        albumResp = albumClient.findById(item.getAlbumId());
                    } catch (Exception e) {
                        log.warn("Failed to fetch album {} for list {}: {}", item.getAlbumId(), listId, e.getMessage());
                    }
                    if (albumResp == null) {
                        albumResp = new com.aficio.dto.album.AlbumResponse(
                                item.getAlbumId(),
                                null,
                                null,
                                null,
                                null
                        );
                    }
                    return new MusicListItemResponse(
                            item.getId(),
                            albumResp,
                            item.getPosition()
                    );
                })
                .collect(Collectors.toList());
    }

    private com.aficio.dto.user.UserSummaryResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        com.aficio.dto.user.UserSummaryResponse user = userClient.findByUsername(authentication.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found or unauthenticated");
        }
        return user;
    }

    public record ListRequest(
            @NotBlank(message = "Title is required")
            String title,
            String description,
            boolean isPublic
    ) {
    }
}