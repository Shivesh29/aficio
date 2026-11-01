package com.aficio.social;

import com.aficio.message.DirectMessage;
import com.aficio.message.DirectMessageRepository;
import com.aficio.review.Review;
import com.aficio.review.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SocialServiceIntegrationTest {

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Test
    void shouldTrackDirectMessageReadStatus() {
        DirectMessage dm = new DirectMessage(1L, 2L, "Hey there!");
        assertThat(dm.isRead()).isFalse();
        assertThat(dm.getContent()).isEqualTo("Hey there!");

        dm.setRead(true);
        dm.setReadAt(LocalDateTime.now());

        assertThat(dm.isRead()).isTrue();
        assertThat(dm.getReadAt()).isNotNull();
    }

    @Test
    void shouldFindReviewById() {
        Review review = new Review(1L, 100L, 4.5, "Incredible production and mixing.");
        when(reviewRepository.findById(42L)).thenReturn(Optional.of(review));

        Optional<Review> found = reviewRepository.findById(42L);

        assertThat(found).isPresent();
        assertThat(found.get().getRating()).isEqualTo(4.5);
        assertThat(found.get().getContent()).isEqualTo("Incredible production and mixing.");
    }
}
