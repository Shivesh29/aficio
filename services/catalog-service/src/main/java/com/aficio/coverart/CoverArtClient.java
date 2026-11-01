package com.aficio.coverart;

import java.io.IOException;
import java.net.http.HttpClient;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CoverArtClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoverArtClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl("https://coverartarchive.org")
                .defaultHeader(
                        "User-Agent",
                        "Aficio/0.0.1 (contact@example.com)"
                )
                .build();
    }

    public String findFrontCover(String releaseId) {
        return findFrontCover(releaseId, null);
    }

    public String findFrontCoverByReleaseGroup(String releaseGroupId) {
        if (releaseGroupId == null || releaseGroupId.isBlank()) {
            return null;
        }
        return fetchCoverFromUri("/release-group/{id}", releaseGroupId);
    }

    public String findFrontCover(String releaseId, String releaseGroupId) {
        if (releaseGroupId != null && !releaseGroupId.isBlank()) {
            String url = fetchCoverFromUri("/release-group/{id}", releaseGroupId);
            if (url != null) {
                return url;
            }
        }

        if (releaseId != null && !releaseId.isBlank()) {
            String url = fetchCoverFromUri("/release/{id}", releaseId);
            if (url != null) {
                return url;
            }
        }

        if (releaseId != null && !releaseId.isBlank() && releaseGroupId == null) {
            String url = fetchCoverFromUri("/release-group/{id}", releaseId);
            if (url != null) {
                return url;
            }
        }

        return null;
    }

    private String fetchCoverFromUri(String uriTemplate, String id) {
        try {
            String responseBody = restClient.get()
                    .uri(uriTemplate, id)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }

            CoverArtResponse response = objectMapper.readValue(
                    responseBody,
                    CoverArtResponse.class
            );

            if (response.images() == null) {
                return null;
            }

            return response.images()
                    .stream()
                    .filter(CoverArtImage::front)
                    .map(this::chooseImageUrl)
                    .findFirst()
                    .orElse(null);

        } catch (RestClientException | IOException exception) {
            return null;
        }
    }

    private String chooseImageUrl(CoverArtImage image) {
        String url = null;
        if (image.thumbnails() != null && image.thumbnails().large() != null) {
            url = image.thumbnails().large();
        } else {
            url = image.image();
        }
        if (url != null && url.startsWith("http://")) {
            url = "https://" + url.substring(7);
        }
        return url;
    }
}