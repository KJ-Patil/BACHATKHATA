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

    public Country(String name, String isoCode, String dialCode) {
        this.name = name;
        this.isoCode = isoCode;
        this.dialCode = dialCode;
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
        list.add(new Country("India", "IN", "+91"));
        list.add(new Country("United States", "US", "+1"));
        list.add(new Country("United Kingdom", "GB", "+44"));
        list.add(new Country("Canada", "CA", "+1"));
        list.add(new Country("Australia", "AU", "+61"));
        list.add(new Country("United Arab Emirates", "AE", "+971"));
        list.add(new Country("Saudi Arabia", "SA", "+966"));
        list.add(new Country("Singapore", "SG", "+65"));
        list.add(new Country("Malaysia", "MY", "+60"));
        list.add(new Country("Qatar", "QA", "+974"));
        list.add(new Country("Kuwait", "KW", "+965"));
        list.add(new Country("Germany", "DE", "+49"));
        list.add(new Country("France", "FR", "+33"));
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
}
