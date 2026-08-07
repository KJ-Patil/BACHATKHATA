package com.example.bachatkhata;

import java.util.Arrays;
import java.util.List;

/**
 * Speech-recognition languages offered for voice logging (ANDROID_FEATURES.md §5.5).
 *
 * <p>Distinct from the app's UI language (`LocaleHelper`, en/hi/mr): a user reading
 * the app in English may well speak their transactions in Marathi or Tamil, so the
 * two settings are independent. Only the dictation locale changes here — the parser
 * that turns the recognized text into an amount and category is shared, since it
 * already handles Hinglish and Devanagari digits.
 */
public final class VoiceLanguages {

    /** BCP-47 tag used when the user has not chosen one. */
    public static final String DEFAULT_TAG = "hi-IN";

    public static final class Option {
        public final String tag;
        public final String englishName;
        public final String nativeName;

        Option(String tag, String englishName, String nativeName) {
            this.tag = tag;
            this.englishName = englishName;
            this.nativeName = nativeName;
        }

        /** Label for the picker: endonym first, since that is what a speaker looks for. */
        public String label() {
            return nativeName + "  ·  " + englishName;
        }
    }

    /**
     * The nine languages offered. Ordered by speaker count rather than
     * alphabetically, so the common picks are reachable without scrolling.
     */
    private static final List<Option> OPTIONS = Arrays.asList(
            new Option("hi-IN", "Hindi", "हिन्दी"),
            new Option("en-IN", "English (India)", "English"),
            new Option("mr-IN", "Marathi", "मराठी"),
            new Option("bn-IN", "Bengali", "বাংলা"),
            new Option("ta-IN", "Tamil", "தமிழ்"),
            new Option("te-IN", "Telugu", "తెలుగు"),
            new Option("gu-IN", "Gujarati", "ગુજરાતી"),
            new Option("kn-IN", "Kannada", "ಕನ್ನಡ"),
            new Option("ml-IN", "Malayalam", "മലയാളം")
    );

    private VoiceLanguages() {
    }

    public static List<Option> all() {
        return OPTIONS;
    }

    /** The option for {@code tag}, falling back to the default rather than null. */
    public static Option get(String tag) {
        for (Option option : OPTIONS) {
            if (option.tag.equals(tag)) return option;
        }
        return OPTIONS.get(0);
    }

    public static String[] labels() {
        String[] labels = new String[OPTIONS.size()];
        for (int i = 0; i < OPTIONS.size(); i++) {
            labels[i] = OPTIONS.get(i).label();
        }
        return labels;
    }

    /** Index of {@code tag} in {@link #all()}, or 0 when it is not a known tag. */
    public static int indexOf(String tag) {
        for (int i = 0; i < OPTIONS.size(); i++) {
            if (OPTIONS.get(i).tag.equals(tag)) return i;
        }
        return 0;
    }
}
