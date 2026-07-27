package com.example.bachatkhata;

import java.util.ArrayList;
import java.util.List;

/**
 * A country with its ISO code and international dial code, powering the
 * phone-number input's country-code selector (mirrors the web app's
 * {@code countries.ts}).
 */
public class Country {
    public final String name;
    public final String isoCode; // ISO 3166-1 alpha-2, e.g. "IN"
    public final String dialCode; // e.g. "+91"
    public final int minDigits; // shortest valid national number
    public final int maxDigits; // longest valid national number

    public Country(String name, String isoCode, String dialCode) {
        // Sensible default range for countries whose exact limits aren't specified:
        // wide enough not to reject valid numbers, narrow enough to catch typos.
        this(name, isoCode, dialCode, 7, 12);
    }

    public Country(String name, String isoCode, String dialCode, int minDigits, int maxDigits) {
        this.name = name;
        this.isoCode = isoCode;
        this.dialCode = dialCode;
        this.minDigits = minDigits;
        this.maxDigits = maxDigits;
    }

    /** Flag emoji derived from the ISO code via Unicode regional indicator symbols. */
    public String flag() {
        if (isoCode == null || isoCode.length() != 2) return "🏳";
        int first = Character.codePointAt(isoCode.toUpperCase(), 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(isoCode.toUpperCase(), 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    /** Label shown in the picker list, e.g. "🇮🇳  India (+91)". */
    public String displayLabel() {
        return flag() + "  " + name + " (" + dialCode + ")";
    }

    /** A curated list of common countries, India first as the default. */
    public static List<Country> all() {
        List<Country> list = new ArrayList<>();
        list.add(new Country("India", "IN", "+91", 10, 10));
        list.add(new Country("United States", "US", "+1", 10, 10));
        list.add(new Country("United Kingdom", "GB", "+44", 9, 10));
        list.add(new Country("Canada", "CA", "+1", 10, 10));
        list.add(new Country("Australia", "AU", "+61", 9, 9));
        list.add(new Country("United Arab Emirates", "AE", "+971", 9, 9));
        list.add(new Country("Saudi Arabia", "SA", "+966", 9, 9));
        list.add(new Country("Singapore", "SG", "+65", 8, 8));
        list.add(new Country("Malaysia", "MY", "+60", 9, 10));
        list.add(new Country("Qatar", "QA", "+974", 8, 8));
        list.add(new Country("Kuwait", "KW", "+965", 8, 8));
        list.add(new Country("Germany", "DE", "+49", 10, 11));
        list.add(new Country("France", "FR", "+33", 9, 9));
        list.add(new Country("Italy", "IT", "+39"));
        list.add(new Country("Spain", "ES", "+34"));
        list.add(new Country("Netherlands", "NL", "+31"));
        list.add(new Country("Switzerland", "CH", "+41"));
        list.add(new Country("Japan", "JP", "+81"));
        list.add(new Country("China", "CN", "+86"));
        list.add(new Country("Hong Kong", "HK", "+852"));
        list.add(new Country("Indonesia", "ID", "+62"));
        list.add(new Country("Thailand", "TH", "+66"));
        list.add(new Country("Philippines", "PH", "+63"));
        list.add(new Country("Vietnam", "VN", "+84"));
        list.add(new Country("Bangladesh", "BD", "+880"));
        list.add(new Country("Pakistan", "PK", "+92"));
        list.add(new Country("Sri Lanka", "LK", "+94"));
        list.add(new Country("Nepal", "NP", "+977"));
        list.add(new Country("Brazil", "BR", "+55"));
        list.add(new Country("South Africa", "ZA", "+27"));
        list.add(new Country("Nigeria", "NG", "+234"));
        list.add(new Country("Kenya", "KE", "+254"));
        list.add(new Country("New Zealand", "NZ", "+64"));
        list.add(new Country("Ireland", "IE", "+353"));
        list.add(new Country("Russia", "RU", "+7"));
        list.add(new Country("Turkey", "TR", "+90"));
        list.add(new Country("Mexico", "MX", "+52"));
        return list;
    }

    /** Strips everything but digits from a raw phone string. */
    public static String digitsOnly(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "");
    }

    public static Country getByIso(String iso2) {
        if (iso2 == null) return null;
        for (Country c : all()) {
            if (c.isoCode.equalsIgnoreCase(iso2)) return c;
        }
        return null;
    }

    public static Country getByCurrency(String currency) {
        // Only a handful map 1:1 to a currency; used as a best-effort default.
        if (currency == null) return null;
        String cur = currency.toUpperCase();
        for (Country c : all()) {
            if (("IN".equals(c.isoCode) && "INR".equals(cur))
                    || ("US".equals(c.isoCode) && "USD".equals(cur))
                    || ("GB".equals(c.isoCode) && "GBP".equals(cur))
                    || ("AU".equals(c.isoCode) && "AUD".equals(cur))) {
                return c;
            }
        }
        return null;
    }

    /**
     * Validates a national number against this country's digit-length range.
     *
     * @return null when valid, or a human-readable error to show the user
     */
    public String validatePhone(String nationalNumber) {
        String digits = digitsOnly(nationalNumber);
        if (digits.isEmpty()) {
            return "Please enter a phone number.";
        }
        if (digits.length() < minDigits || digits.length() > maxDigits) {
            if (minDigits == maxDigits) {
                return "Enter a valid " + minDigits + "-digit number for " + name + ".";
            }
            return "Enter a valid " + minDigits + "–" + maxDigits + " digit number for " + name + ".";
        }
        return null;
    }

    /** Builds the E.164 form: {@code +<dial><national digits>}. */
    public String toFullNumber(String nationalNumber) {
        return dialCode + digitsOnly(nationalNumber);
    }

    /**
     * Country-agnostic sanity check for a phone field with no country selector
     * (e.g. a ledger contact that may be domestic or foreign). Enforces only the
     * E.164 bounds — 7 to 15 digits — which is enough to reject a mistyped
     * 4-digit "number" without rejecting a legitimate international one.
     *
     * @return null when acceptable, or an error message
     */
    public static String validateLoosePhone(String rawNumber) {
        String digits = digitsOnly(rawNumber);
        if (digits.isEmpty()) {
            return "Please enter a phone number.";
        }
        if (digits.length() < 7 || digits.length() > 15) {
            return "Enter a valid phone number.";
        }
        return null;
    }
}
