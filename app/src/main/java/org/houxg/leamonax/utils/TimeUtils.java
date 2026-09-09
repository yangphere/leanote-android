package org.houxg.leamonax.utils;


import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

import java.util.Calendar;

public class TimeUtils {
    public static final String TAG = "TimeUtils:";

    public static long toTimestamp(String serverTime) {
        return DateTime.parse(normalizeTimezoneOffset(normalizeFractionalSeconds(serverTime))).getMillis();
    }

    private static String normalizeTimezoneOffset(String serverTime) {
        int signIndex = Math.max(serverTime.lastIndexOf('+'), serverTime.lastIndexOf('-'));
        int colonIndex = serverTime.lastIndexOf(':');
        if (signIndex <= serverTime.indexOf('T') || colonIndex <= signIndex) {
            return serverTime;
        }

        String hours = serverTime.substring(signIndex + 1, colonIndex);
        String minutes = serverTime.substring(colonIndex + 1);
        if (hours.length() > 2 || minutes.length() > 2 || !isDigits(hours) || !isDigits(minutes)) {
            return serverTime;
        }
        return serverTime.substring(0, signIndex + 1)
                + padTwoDigits(hours)
                + ":"
                + padTwoDigits(minutes);
    }

    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String padTwoDigits(String value) {
        return value.length() == 1 ? "0" + value : value;
    }

    private static String normalizeFractionalSeconds(String serverTime) {
        int fractionStart = serverTime.indexOf('.');
        if (fractionStart < 0) {
            return serverTime;
        }

        int fractionEnd = fractionStart + 1;
        while (fractionEnd < serverTime.length()
                && Character.isDigit(serverTime.charAt(fractionEnd))) {
            fractionEnd++;
        }
        if (fractionEnd - fractionStart <= 4) {
            return serverTime;
        }
        return serverTime.substring(0, fractionStart + 4) + serverTime.substring(fractionEnd);
    }

    public static String toServerTime(long timeInMills) {
        return DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(DateTimeZone.UTC)
                .print(timeInMills);
    }

    public static String toTimeFormat(long timeInMills) {
        return DateTimeFormat.forPattern("H:mm:ss").print(timeInMills);
    }

    public static String toDateFormat(long timeInMills) {
        return DateTimeFormat.forPattern("M-dd H:mm:ss").print(timeInMills);
    }

    public static String toYearFormat(long timeInMills) {
        return DateTimeFormat.forPattern("yyyy-M-dd H:mm:ss").print(timeInMills);
    }

    public static Calendar getToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    public static Calendar getYesterday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return calendar;
    }

    public static Calendar getThisYear() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.DAY_OF_YEAR, 0);
        return calendar;
    }


}
