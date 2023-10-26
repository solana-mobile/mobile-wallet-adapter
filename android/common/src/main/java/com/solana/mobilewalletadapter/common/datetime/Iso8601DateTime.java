package com.solana.mobilewalletadapter.common.datetime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Iso8601DateTime {

    static final String ISO_8601_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    static final String ISO_8601_FORMAT_STRING_NO_ZONE = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    public static String now() {
        return formatUtc(new Date());
    }

    public static String formatUtc(Date date) {
        SimpleDateFormat format = new SimpleDateFormat(ISO_8601_FORMAT_STRING_NO_ZONE, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(date) + "Z";
    }

    public static Date parse(String iso8601String) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat(ISO_8601_FORMAT_STRING, Locale.US);
        try {
            String formattedDate = iso8601String;
            if (formattedDate.endsWith("Z")) {
                // SimpleDateFormat does not comprehend "Z" (UTC), so replace it
                formattedDate = formattedDate.replace("Z", "+0000");
            } else {
                // SimpleDateFormat requires zone to be in +/-hhmm, so remove ":"
                formattedDate = formattedDate.replaceAll("([+-]\\d\\d):(\\d\\d)\\s*$", "$1$2");
            }
            // add microseconds field if missing
            formattedDate = formattedDate.replaceAll("(T\\d\\d)(:\\d\\d)(:\\d\\d)([+-])", "$1$2$3.000$4");

            return format.parse(formattedDate);
        } catch (ParseException e) {
            throw new ParseException("Failed to parse input as ISO 8601", e.getErrorOffset());
        }
    }

    private Iso8601DateTime() {}
}
