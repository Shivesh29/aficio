package com.aficio.config;

import com.aficio.dto.album.AlbumResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "catalog-service",
        url = "${aficio.catalog.service-url:http://localhost:8082}",
        fallback = AlbumClient.AlbumClientFallback.class
)
public interface AlbumClient {

    @GetMapping("/albums/{id}")
    AlbumResponse findById(@PathVariable Long id);

    class AlbumClientFallback implements AlbumClient {
        @Override
        public AlbumResponse findById(Long id) {
            return null;
        }
    }
}