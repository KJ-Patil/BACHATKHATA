package com.example.bachatkhata.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ReminderServiceTest {

    @Test
    public void everyCombinationProducesDistinctNonEmptyCopy() {
        Set<String> seen = new HashSet<>();
        for (ReminderService.Tone tone : ReminderService.Tone.values()) {
            for (ReminderService.Lang lang : ReminderService.Lang.values()) {
                for (ReminderService.Relation relation : ReminderService.Relation.values()) {
                    String message = ReminderService.generate("Asha", "₹1,200", tone, lang, relation);

                    assertFalse("empty copy for " + tone + "/" + lang + "/" + relation,
                            message.trim().isEmpty());
                    // A missed switch case would silently reuse another template.
                    assertTrue("duplicate copy for " + tone + "/" + lang + "/" + relation,
                            seen.add(message));
                }
            }
        }
        assertEquals(27, seen.size());
    }

    @Test
    public void substitutesNameAndAmount() {
        String message = ReminderService.generate("Asha", "₹1,200",
                ReminderService.Tone.FRIENDLY, ReminderService.Lang.EN,
                ReminderService.Relation.CREDIT);

        assertTrue(message.contains("Asha"));
        assertTrue(message.contains("₹1,200"));
        // No unsubstituted format specifiers left behind.
        assertFalse(message.contains("%1$s"));
        assertFalse(message.contains("%2$s"));
    }

    @Test
    public void fallsBackWhenNameIsMissing() {
        String message = ReminderService.generate(null, "₹500",
                ReminderService.Tone.FORMAL, ReminderService.Lang.EN,
                ReminderService.Relation.CREDIT);
        assertTrue(message.contains("there"));

        String blank = ReminderService.generate("   ", "₹500",
                ReminderService.Tone.FORMAL, ReminderService.Lang.EN,
                ReminderService.Relation.CREDIT);
        assertTrue(blank.contains("there"));
    }

    @Test
    public void normalizesIndianPhoneNumbers() {
        assertEquals("919876543210", ReminderService.normalizePhoneNumber("9876543210"));
        assertEquals("919876543210", ReminderService.normalizePhoneNumber("98765 43210"));
        assertEquals("919876543210", ReminderService.normalizePhoneNumber("+91 98765-43210"));
        assertEquals("919876543210", ReminderService.normalizePhoneNumber("09876543210"));
    }

    @Test
    public void leavesForeignNumbersAlone() {
        // A US number is already 11 digits with its country code and must not
        // have +91 stapled onto it.
        assertEquals("14155552671", ReminderService.normalizePhoneNumber("+1 415 555 2671"));
    }

    @Test
    public void handlesNullAndEmptyPhone() {
        assertEquals("", ReminderService.normalizePhoneNumber(null));
        assertEquals("", ReminderService.normalizePhoneNumber(""));
    }

    @Test
    public void buildsWhatsAppLink() {
        String url = ReminderService.whatsAppUrl("9876543210", "Hi Asha, ₹100 due");
        assertTrue(url.startsWith("https://wa.me/919876543210?text="));
        // Spaces must be percent-encoded, not left bare or turned into '+'.
        assertFalse(url.contains(" "));
        assertFalse(url.substring(url.indexOf("?text=")).contains("+"));
    }

    @Test
    public void buildsSmsLink() {
        String uri = ReminderService.smsUri("+91 98765 43210", "Hi Asha");
        assertTrue(uri.startsWith("sms:+919876543210?body="));
    }
}
