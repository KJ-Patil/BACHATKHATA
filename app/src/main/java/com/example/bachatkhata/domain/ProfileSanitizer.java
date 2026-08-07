package com.example.bachatkhata.domain;

import android.net.Uri;

/**
 * Validation for the two profile fields that come back out of Firestore and land
 * directly in a rendered view: the display name and the avatar URL.
 *
 * <p>Port of {@code AvatarUtils} (ANDROID_FEATURES.md §5.24). These are security
 * helpers, not formatting helpers — the {@code users/{uid}} document is
 * user-writable, so a value read back out of it is untrusted input even though
 * this app is what wrote it. Both checks therefore run on the way <em>in</em> and
 * on the way <em>out</em>.
 */
public final class ProfileSanitizer {

    /** Display names longer than this are truncated. */
    public static final int MAX_NAME_CHARS = 40;

    /** Name shown when nothing usable survives sanitization. */
    public static final String FALLBACK_NAME = "Guest";

    private ProfileSanitizer() {
    }

    /**
     * True when {@code value} is a URL this app is willing to hand to an image
     * loader. Exactly two shapes are ever legitimate:
     *
     * <ul>
     *   <li>an {@code https:} URL — what Firebase Storage and the identity
     *       providers return;</li>
     *   <li>a base64 image {@code data:} URL (jpeg / png / webp) — the inline
     *       form the web app stores.</li>
     * </ul>
     *
     * <p>Everything else is rejected: {@code javascript:}, {@code data:text/html},
     * {@code blob:}, plain {@code http:}, and — importantly on Android —
     * {@code file:} and {@code content:}, which Glide would happily resolve into
     * a local file read.
     *
     * <p>The scheme is taken from a real parse rather than a {@code startsWith}
     * test, because {@code "https:evil"} passes the string check and fails the
     * parse.
     */
    public static boolean isSafeAvatarUrl(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;

        // Control characters can split a URL across a line in logs or headers;
        // no legitimate URL contains them.
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) < 0x20 || trimmed.charAt(i) == 0x7F) return false;
        }

        String scheme;
        try {
            scheme = Uri.parse(trimmed).getScheme();
        } catch (Exception e) {
            return false;
        }
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(java.util.Locale.US);

        if ("https".equals(scheme)) {
            // Reject "https:evil" — a hierarchical URL needs an authority.
            return trimmed.regionMatches(true, 0, "https://", 0, 8)
                    && trimmed.length() > 8;
        }
        if ("data".equals(scheme)) {
            String lower = trimmed.toLowerCase(java.util.Locale.US);
            return lower.startsWith("data:image/jpeg;base64,")
                    || lower.startsWith("data:image/jpg;base64,")
                    || lower.startsWith("data:image/png;base64,")
                    || lower.startsWith("data:image/webp;base64,");
        }
        return false;
    }

    /**
     * Returns {@code value} when it passes {@link #isSafeAvatarUrl}, otherwise null.
     * Null is meaningful and must be persisted as such — see §6.6: it records that
     * the user <em>removed</em> their photo, which is what stops the identity
     * provider's photo being re-seeded at the next sign-in.
     */
    public static String sanitizeAvatarUrl(String value) {
        return isSafeAvatarUrl(value) ? value.trim() : null;
    }

    /**
     * Strips from a display name every character that must never survive into
     * rendered text, then collapses whitespace and caps the length.
     *
     * <p>Removed outright: C0/C1 controls, {@code DEL}, the zero-width marks
     * ({@code U+200B–200F}) and the bidirectional overrides
     * ({@code U+202A–202E}, {@code U+2066–2069}) — the last group is the reason
     * this exists, since they let one name be rendered to look like another.
     *
     * <p>Tab, newline and carriage return become a space instead of vanishing, so
     * {@code "First\nLast"} collapses to {@code "First Last"} rather than
     * {@code "FirstLast"}.
     */
    public static String sanitizeDisplayName(String value) {
        if (value == null) return "";

        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '\t' || c == '\n' || c == '\r') {
                sb.append(' ');
                continue;
            }
            if (c < 0x20 || c == 0x7F) continue;                 // C0 + DEL
            if (c >= 0x80 && c <= 0x9F) continue;                // C1
            if (c >= 0x200B && c <= 0x200F) continue;            // zero-width / LRM / RLM
            if (c >= 0x202A && c <= 0x202E) continue;            // bidi embedding + override
            if (c >= 0x2066 && c <= 0x2069) continue;            // bidi isolate
            if (c == 0xFEFF) continue;                           // BOM / zero-width no-break

            sb.append(c);
        }

        String collapsed = sb.toString().replaceAll("\\s+", " ").trim();
        if (collapsed.length() > MAX_NAME_CHARS) {
            collapsed = collapsed.substring(0, MAX_NAME_CHARS).trim();
        }
        return collapsed;
    }

    /**
     * {@link #sanitizeDisplayName} with a guaranteed non-empty result — for the
     * read path, where a name that sanitizes down to nothing still has to render
     * as something.
     */
    public static String sanitizeDisplayNameOrFallback(String value, String fallback) {
        String clean = sanitizeDisplayName(value);
        if (!clean.isEmpty()) return clean;
        String cleanFallback = sanitizeDisplayName(fallback);
        return cleanFallback.isEmpty() ? FALLBACK_NAME : cleanFallback;
    }
}
