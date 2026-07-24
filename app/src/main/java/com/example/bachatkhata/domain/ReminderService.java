package com.example.bachatkhata.domain;

import java.util.Locale;

/**
 * Templated payment reminders for the ledger and bill splitter.
 *
 * <p>Three tones × three languages × three relations of copy, all editable by the
 * user before sending. Dispatch is via plain {@code Intent}s to WhatsApp or the
 * device SMS app — the app never claims to have sent anything itself.
 *
 * <p>Pure Java (no Android imports) so the copy matrix is unit-testable.
 */
public final class ReminderService {

    private ReminderService() {
    }

    /** How firmly the message is worded. */
    public enum Tone { FRIENDLY, FORMAL, URGENT }

    /** Which language the message is written in. */
    public enum Lang { EN, HI, MR }

    /**
     * Who owes whom, from the sender's point of view.
     *
     * <ul>
     *   <li>{@link #CREDIT} — they owe you; this is a request to pay.</li>
     *   <li>{@link #DEBIT} — you owe them; this acknowledges the debt.</li>
     *   <li>{@link #SETTLEMENT} — a split-bill settlement between equals.</li>
     * </ul>
     */
    public enum Relation { CREDIT, DEBIT, SETTLEMENT }

    /**
     * Builds the reminder body.
     *
     * @param name           the other party's display name
     * @param formattedAmount the amount already formatted with its currency symbol
     *                        (this class does no money formatting of its own —
     *                        {@code CurrencyManager} owns that)
     */
    public static String generate(String name, String formattedAmount,
                                  Tone tone, Lang lang, Relation relation) {
        String safeName = (name == null || name.trim().isEmpty()) ? "there" : name.trim();
        String amount = formattedAmount == null ? "" : formattedAmount;
        return String.format(Locale.US, template(tone, lang, relation), safeName, amount);
    }

    /**
     * Template for one tone/language/relation combination, with {@code %1$s} for
     * the name and {@code %2$s} for the amount.
     */
    private static String template(Tone tone, Lang lang, Relation relation) {
        switch (lang) {
            case HI:
                return hindi(tone, relation);
            case MR:
                return marathi(tone, relation);
            case EN:
            default:
                return english(tone, relation);
        }
    }

    // ── English ─────────────────────────────────────────────────────────────

    private static String english(Tone tone, Relation relation) {
        switch (relation) {
            case DEBIT:
                switch (tone) {
                    case FORMAL:
                        return "Dear %1$s, this is to confirm an outstanding balance of %2$s payable by me. I will arrange the payment shortly.";
                    case URGENT:
                        return "Hi %1$s, I know %2$s is pending from my side. I am settling it right away — apologies for the delay.";
                    case FRIENDLY:
                    default:
                        return "Hi %1$s! Just a note that I still owe you %2$s. I'll clear it soon — thanks for your patience!";
                }
            case SETTLEMENT:
                switch (tone) {
                    case FORMAL:
                        return "Dear %1$s, as per our shared expense settlement, an amount of %2$s is due. Kindly transfer at your convenience.";
                    case URGENT:
                        return "Hi %1$s, the split settlement of %2$s is still open. Please transfer it today so I can close the accounts.";
                    case FRIENDLY:
                    default:
                        return "Hey %1$s! From our shared expenses, your share comes to %2$s. Send it across whenever you get a chance!";
                }
            case CREDIT:
            default:
                switch (tone) {
                    case FORMAL:
                        return "Dear %1$s, our records show an outstanding balance of %2$s against your account. Kindly arrange the payment at the earliest.";
                    case URGENT:
                        return "Hi %1$s, the pending amount of %2$s is now overdue. Please settle it today to avoid further follow-ups.";
                    case FRIENDLY:
                    default:
                        return "Hi %1$s! Hope you're doing well. Just a gentle reminder about the pending %2$s. Whenever convenient — thank you!";
                }
        }
    }

    // ── Hindi ───────────────────────────────────────────────────────────────

    private static String hindi(Tone tone, Relation relation) {
        switch (relation) {
            case DEBIT:
                switch (tone) {
                    case FORMAL:
                        return "आदरणीय %1$s, मेरी ओर से %2$s की राशि बकाया है। मैं शीघ्र ही भुगतान की व्यवस्था कर रहा/रही हूँ।";
                    case URGENT:
                        return "नमस्ते %1$s, मुझे पता है कि मेरी ओर से %2$s बाकी है। मैं इसे तुरंत चुका रहा/रही हूँ — देरी के लिए क्षमा करें।";
                    case FRIENDLY:
                    default:
                        return "नमस्ते %1$s! याद दिला दूँ कि मुझ पर आपके %2$s बाकी हैं। जल्द ही चुका दूँगा/दूँगी — धन्यवाद!";
                }
            case SETTLEMENT:
                switch (tone) {
                    case FORMAL:
                        return "आदरणीय %1$s, साझा खर्च के हिसाब से %2$s की राशि देय है। कृपया सुविधानुसार भेज दें।";
                    case URGENT:
                        return "नमस्ते %1$s, %2$s का हिसाब अब तक बाकी है। कृपया आज ही भेज दें ताकि हिसाब पूरा हो सके।";
                    case FRIENDLY:
                    default:
                        return "हाय %1$s! हमारे साझा खर्च में आपका हिस्सा %2$s बनता है। जब समय मिले तब भेज देना!";
                }
            case CREDIT:
            default:
                switch (tone) {
                    case FORMAL:
                        return "आदरणीय %1$s, हमारे रिकॉर्ड के अनुसार आपके खाते पर %2$s बकाया है। कृपया शीघ्र भुगतान करें।";
                    case URGENT:
                        return "नमस्ते %1$s, %2$s की बकाया राशि की अवधि निकल चुकी है। कृपया आज ही भुगतान करें।";
                    case FRIENDLY:
                    default:
                        return "नमस्ते %1$s! आशा है आप कुशल होंगे। %2$s बाकी होने की एक विनम्र याद — जब सुविधा हो, धन्यवाद!";
                }
        }
    }

    // ── Marathi ─────────────────────────────────────────────────────────────

    private static String marathi(Tone tone, Relation relation) {
        switch (relation) {
            case DEBIT:
                switch (tone) {
                    case FORMAL:
                        return "आदरणीय %1$s, माझ्याकडून %2$s रक्कम येणे बाकी आहे. मी लवकरच भरणा करत आहे.";
                    case URGENT:
                        return "नमस्कार %1$s, माझ्याकडून %2$s बाकी आहे याची कल्पना आहे. मी लगेच भरतो/भरते — उशिरासाठी क्षमस्व.";
                    case FRIENDLY:
                    default:
                        return "नमस्कार %1$s! आठवण म्हणून — माझ्याकडे तुमचे %2$s बाकी आहेत. लवकरच देतो/देते, धन्यवाद!";
                }
            case SETTLEMENT:
                switch (tone) {
                    case FORMAL:
                        return "आदरणीय %1$s, सामायिक खर्चाच्या हिशोबानुसार %2$s रक्कम देय आहे. कृपया सोयीनुसार पाठवा.";
                    case URGENT:
                        return "नमस्कार %1$s, %2$s चा हिशोब अजून बाकी आहे. कृपया आजच पाठवा म्हणजे हिशोब पूर्ण होईल.";
                    case FRIENDLY:
                    default:
                        return "हाय %1$s! आपल्या सामायिक खर्चात तुमचा वाटा %2$s होतो. वेळ मिळेल तेव्हा पाठवा!";
                }
            case CREDIT:
            default:
                switch (tone) {
                    case FORMAL:
                        return "आदरणीय %1$s, आमच्या नोंदीनुसार तुमच्या खात्यावर %2$s थकीत आहे. कृपया लवकरात लवकर भरणा करा.";
                    case URGENT:
                        return "नमस्कार %1$s, %2$s ची थकीत रक्कम मुदतीबाहेर गेली आहे. कृपया आजच भरणा करा.";
                    case FRIENDLY:
                    default:
                        return "नमस्कार %1$s! आशा आहे तुम्ही ठीक असाल. %2$s बाकी असल्याची नम्र आठवण — सोयीनुसार, धन्यवाद!";
                }
        }
    }

    // ── Dispatch helpers ────────────────────────────────────────────────────

    /**
     * Strips everything but digits, then applies India's default country code when
     * the number is a bare 10-digit local one. WhatsApp rejects numbers without a
     * country code outright, so a local number would otherwise fail silently.
     */
    public static String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");

        if (digits.length() == 10) {
            return "91" + digits;
        }
        // "0" trunk prefix on an 11-digit Indian number.
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "91" + digits.substring(1);
        }
        return digits;
    }

    /** {@code https://wa.me/<digits>?text=…} — opens WhatsApp with the draft ready. */
    public static String whatsAppUrl(String phone, String message) {
        return "https://wa.me/" + normalizePhoneNumber(phone)
                + "?text=" + urlEncode(message);
    }

    /**
     * {@code sms:<number>?body=…} — hands off to the device SMS app. Uses the
     * number as typed, not the normalized form: the local dialer handles local
     * formats fine, and rewriting it can break carrier short codes.
     */
    public static String smsUri(String phone, String message) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        return "sms:" + digits + "?body=" + urlEncode(message);
    }

    private static String urlEncode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is guaranteed present on every JVM; unreachable in practice.
            return value;
        }
    }
}
