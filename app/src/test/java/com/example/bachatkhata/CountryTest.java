package com.example.bachatkhata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CountryTest {

    private static Country india() {
        return Country.getByIso("IN");
    }

    @Test
    public void digitsOnlyStripsFormatting() {
        assertEquals("919876543210", Country.digitsOnly("+91 98765-43210"));
        assertEquals("9876543210", Country.digitsOnly("98765-43210"));
        assertEquals("", Country.digitsOnly(null));
        assertEquals("", Country.digitsOnly("abc"));
    }

    @Test
    public void validatesIndianTenDigitNumber() {
        assertNull(india().validatePhone("9876543210"));
        assertNull(india().validatePhone("98765 43210"));
    }

    @Test
    public void rejectsWrongLengthForCountry() {
        assertNotNull(india().validatePhone("98765"));       // too short
        assertNotNull(india().validatePhone("987654321012")); // too long
        assertNotNull(india().validatePhone(""));             // empty
    }

    @Test
    public void buildsE164() {
        assertEquals("+919876543210", india().toFullNumber("98765 43210"));
        assertEquals("+14155552671", Country.getByIso("US").toFullNumber("(415) 555-2671"));
    }

    @Test
    public void lookupHelpers() {
        assertEquals("IN", india().isoCode);
        assertNull(Country.getByIso("ZZ"));
        assertEquals("IN", Country.getByCurrency("INR").isoCode);
        assertEquals("US", Country.getByCurrency("USD").isoCode);
        assertNull(Country.getByCurrency("XXX"));
    }

    @Test
    public void loosePhoneAcceptsDomesticAndForeign() {
        assertNull(Country.validateLoosePhone("9876543210"));      // Indian 10-digit
        assertNull(Country.validateLoosePhone("+1 415 555 2671")); // US with code
        assertNotNull(Country.validateLoosePhone("1234"));         // too short
        assertNotNull(Country.validateLoosePhone(""));             // empty
    }
}
