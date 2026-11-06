package com.aficio.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalogNormalizationUtilsTest {

    @Test
    void shouldMatchArtistNamesWithVowelAndSpellingVariants() {
        assertThat(CatalogNormalizationUtils.isArtistMatch("Satinder Sartaj", "Satinder Sartaaj")).isTrue();
        assertThat(CatalogNormalizationUtils.isArtistMatch("Satinder Sartaaj", "Satinder Sartaj")).isTrue();
        assertThat(CatalogNormalizationUtils.isArtistMatch("A.R. Rahman", "AR Rahman")).isTrue();
        assertThat(CatalogNormalizationUtils.isArtistMatch("Nusrat Fateh Ali Khan", "Nusrat Fateh Ali Khan")).isTrue();

        // Should not match completely different artists
        assertThat(CatalogNormalizationUtils.isArtistMatch("Satinder Sartaj", "Taylor Swift")).isFalse();
        assertThat(CatalogNormalizationUtils.isArtistMatch("Black Sabbath", "Radiohead")).isFalse();
    }

    @Test
    void shouldMatchTracksWithDurationFingerprintingAndPhoneticTransliteration() {
        // "Pani Panjan Daryawan Wala" (522.98s ~ 523s) vs "Paani Panja Dariyanwan Da" (523s)
        assertThat(CatalogNormalizationUtils.isTrackFuzzyMatch(
                "Pani Panjan Daryawan Wala", 522983,
                "Paani Panja Dariyanwan Da", 523
        )).isTrue();

        // "Gall Tajurbe Wali" (275.16s ~ 275s) vs "Gal Tazrabe Wali" (276s)
        assertThat(CatalogNormalizationUtils.isTrackFuzzyMatch(
                "Gall Tajurbe Wali", 275156,
                "Gal Tazrabe Wali", 276
        )).isTrue();

        // "Nikki Jehi Kuri" (286.98s ~ 287s) vs "Nikki Jehi Kudi" (287s)
        assertThat(CatalogNormalizationUtils.isTrackFuzzyMatch(
                "Nikki Jehi Kuri", 286975,
                "Nikki Jehi Kudi", 287
        )).isTrue();

        // "Sai" (517s) vs "Sai Ve" (518s)
        assertThat(CatalogNormalizationUtils.isTrackFuzzyMatch(
                "Sai", 517038,
                "Sai Ve", 518
        )).isTrue();

        // "Sab Te Laagu" (285.6s ~ 286s) vs "Sabh Te Laggu" (286s)
        assertThat(CatalogNormalizationUtils.isTrackFuzzyMatch(
                "Sab Te Laagu", 285651,
                "Sabh Te Laggu", 286
        )).isTrue();

        // Western albums: Smells Like Teen Spirit vs Smells Like Teen Spirit
        assertThat(CatalogNormalizationUtils.isTrackFuzzyMatch(
                "Smells Like Teen Spirit", 301000,
                "Smells Like Teen Spirit", 301
        )).isTrue();
    }

    @Test
    void shouldNormalizePhoneticStrings() {
        String p1 = CatalogNormalizationUtils.phoneticNormalize("Pani Panjan Daryawan Wala");
        String p2 = CatalogNormalizationUtils.phoneticNormalize("Paani Panja Dariyanwan Da");

        // Both collapse "aa" -> "a", strip "wala" and "da", map "w" -> "v"
        assertThat(p1).contains("pani");
        assertThat(p2).contains("pani");
    }
}

