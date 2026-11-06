package com.aficio.catalog;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class CatalogNormalizationUtils {

    private CatalogNormalizationUtils() {}

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern DASHES = Pattern.compile("[‐‑–—]");
    private static final Pattern QUOTES = Pattern.compile("['’`]");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTI_SPACES = Pattern.compile("\\s+");

    // Common Indic/regional postpositions and particles (Punjabi/Hindi "da", "de", "di", "wala", "wali", "wale", "ve", "ni", "nu", "te")
    private static final Pattern REGIONAL_STOP_WORDS = Pattern.compile("(?i)\\b(da|de|di|wala|wali|wale|ve|we|ni|jeha|jehi|nu|te)\\b");

    /**
     * Collapses repeated vowels and consonants, and normalizes common transliteration variants
     * (e.g. aa -> a, ee/ii -> i, oo/uu -> u, ll -> l, kk -> k, z <-> j, q <-> k, w <-> v).
     */
    public static String phoneticNormalize(String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = DASHES.matcher(s).replaceAll("-");
        s = QUOTES.matcher(s).replaceAll("");
        s = s.toLowerCase();

        // Strip parentheticals / metadata brackets like (2011 Remaster), (feat. X)
        s = s.replaceAll("(?i)\\s*[\\[\\(].*?(remaster|deluxe|bonus|version|edition|mono|stereo|anniversary|feat\\.|ft\\.).*?[\\]\\)]", " ");
        s = s.replaceAll("(?i)\\s*-\\s*(remaster(ed)?|deluxe|bonus track).*$", " ");

        // Strip regional postpositions that frequently vary across catalog entries and MusicBrainz listings
        s = REGIONAL_STOP_WORDS.matcher(s).replaceAll(" ");

        // Transliteration consonant mappings
        s = s.replace('z', 'j');
        s = s.replace('q', 'k');
        s = s.replace('w', 'v');

        // Collapse aspirated consonants common in Indic transliteration (e.g. bh -> b in sabh/sab, dh -> d, kh -> k, jh -> j, gh -> g)
        s = s.replaceAll("([bdgkj])h", "$1");

        // Double vowels collapsed to single
        s = s.replaceAll("aa+", "a");
        s = s.replaceAll("ee+|ii+", "i");
        s = s.replaceAll("oo+|uu+", "u");

        // Double consonants collapsed
        s = s.replaceAll("([b-df-hj-np-tv-z])\\1+", "$1");

        // Keep alphanumeric and single space
        s = NON_ALPHANUMERIC.matcher(s).replaceAll(" ");
        s = MULTI_SPACES.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /**
     * Checks if two artist names match, taking into account vowel variance, transliteration,
     * and minor spelling variations (e.g. "Satinder Sartaj" and "Satinder Sartaaj").
     */
    public static boolean isArtistMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String s1 = name1.trim();
        String s2 = name2.trim();
        if (s1.equalsIgnoreCase(s2)) return true;

        String a1 = s1.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String a2 = s2.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (!a1.isEmpty() && a1.equals(a2)) return true;

        String p1 = phoneticNormalize(s1);
        String p2 = phoneticNormalize(s2);
        if (!p1.isEmpty() && p1.equals(p2)) return true;

        // Levenshtein similarity check for slight transliteration variants
        double sim = calculateStringSimilarity(p1, p2);
        return sim >= 0.85;
    }

    /**
     * Generates spelling variants for an artist name (e.g. expanding/collapsing vowels: sartaj <-> sartaaj).
     */
    public static Set<String> generateArtistVariants(String artistName) {
        if (artistName == null || artistName.isBlank()) return Collections.emptySet();
        Set<String> variants = new LinkedHashSet<>();
        variants.add(artistName.trim());
        String lower = artistName.trim().toLowerCase();
        variants.add(lower);

        // Vowel collapsed variant
        String collapsed = lower.replaceAll("aa+", "a")
                                .replaceAll("ee+|ii+", "i")
                                .replaceAll("oo+|uu+", "u");
        variants.add(collapsed);

        // Common single-a to double-aa expansion in last word (e.g. sartaj -> sartaaj)
        if (lower.contains("a") && !lower.contains("aa")) {
            String[] parts = lower.split("\\s+");
            if (parts.length > 0) {
                String last = parts[parts.length - 1];
                int lastA = last.lastIndexOf('a');
                if (lastA >= 0) {
                    String expandedLast = last.substring(0, lastA) + "aa" + last.substring(lastA + 1);
                    parts[parts.length - 1] = expandedLast;
                    variants.add(String.join(" ", parts));
                }
            }
        }
        return variants;
    }

    /**
     * Levenshtein-based similarity metric (0.0 to 1.0).
     */
    public static double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int maxLen = Math.max(len1, len2);
        int distance = computeLevenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private static int computeLevenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    /**
     * Checks if track duration matches catalog duration within tolerance (in seconds, default +/- 3s).
     */
    public static boolean isDurationMatch(Integer trackLengthMs, Integer catalogDurationSec) {
        if (trackLengthMs == null || catalogDurationSec == null) return false;
        int trackSec = Math.round(trackLengthMs / 1000.0f);
        return Math.abs(trackSec - catalogDurationSec) <= 3;
    }

    /**
     * Checks if two track titles match using duration, phonetic normalization, or word overlap.
     */
    public static boolean isTrackFuzzyMatch(String trackTitle, Integer trackLengthMs,
                                           String catalogTitle, Integer catalogDurationSec) {
        if (trackTitle == null || catalogTitle == null) return false;

        boolean durationMatches = isDurationMatch(trackLengthMs, catalogDurationSec);

        String p1 = phoneticNormalize(trackTitle);
        String p2 = phoneticNormalize(catalogTitle);

        // 1. Exact phonetic match
        if (!p1.isEmpty() && p1.equals(p2)) return true;

        // 2. Substring/prefix match
        if (!p1.isEmpty() && !p2.isEmpty()) {
            if (p1.startsWith(p2) || p2.startsWith(p1)) return true;
            if (p1.contains(p2) || p2.contains(p1)) return true;
        }

        // 3. Significant words overlap (exact or high phonetic similarity >= 0.75 for transliterations)
        String[] w1 = p1.split("\\s+");
        String[] w2 = p2.split("\\s+");

        long commonWords = 0;
        for (String word1 : w1) {
            if (word1.length() < 3) continue;
            for (String word2 : w2) {
                if (word1.equals(word2)
                        || (word1.length() >= 4 && word2.length() >= 4 && calculateStringSimilarity(word1, word2) >= 0.75)) {
                    commonWords++;
                    break;
                }
            }
        }

        int minSignificant = (int) Arrays.stream(w1).filter(w -> w.length() >= 3).count();

        // If duration matches within 3 seconds, even 1 or 2 matching significant words or >=35% similarity is definitive
        if (durationMatches) {
            if (commonWords >= 1) return true;
            if (calculateStringSimilarity(p1, p2) >= 0.35) return true;
        }

        // If duration is missing or doesn't match, require strong title overlap (at least 60% of significant words)
        if (minSignificant > 0 && (double) commonWords / minSignificant >= 0.60) {
            return true;
        }

        return calculateStringSimilarity(p1, p2) >= 0.75;
    }
}
