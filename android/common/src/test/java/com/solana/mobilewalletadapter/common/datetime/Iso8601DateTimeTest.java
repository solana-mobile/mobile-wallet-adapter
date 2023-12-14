package com.solana.mobilewalletadapter.common.datetime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class Iso8601DateTimeTest {

    @Test
    public void testIso8601ParseUtc() throws ParseException {
        // given
        String iso8601String = "2021-01-11T11:15:23.000Z";
        Date expectedDate = Date.from(Instant.parse(iso8601String));

        // when
        Date date = Iso8601DateTime.parse(iso8601String);

        // then
        assertEquals(expectedDate, date);
    }

    @Test
    public void testIso8601ParseWithZone() throws ParseException {
        // given
        String iso8601String = "2021-01-11T11:15:23.000+04:00";
        Date expectedDate = Date.from(ZonedDateTime.parse(iso8601String).toInstant());

        // when
        Date date = Iso8601DateTime.parse(iso8601String);

        // then
        assertEquals(expectedDate, date);
    }

    @Test
    public void testIso8601ParseNoSeconds() throws ParseException {
        // given
        String iso8601String = "2021-01-11T11:15:23Z";
        Date expectedDate = Date.from(Instant.parse(iso8601String));

        // when
        Date date = Iso8601DateTime.parse(iso8601String);

        // then
        assertEquals(expectedDate, date);
    }

    @Test
    public void testIso8601FormatUtc() {
        // given
        String iso8601String = "2021-01-11T11:15:23.000Z";
        Date date = Date.from(Instant.parse(iso8601String));

        // when
        String formattedDate = Iso8601DateTime.formatUtc(date);

        // then
        assertEquals(iso8601String, formattedDate);
    }
}
