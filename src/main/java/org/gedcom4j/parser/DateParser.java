/*
 * Copyright (c) 2009-2016 Matthew R. Harrah
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package org.gedcom4j.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * A class for parsing dates from strings. Slightly more relaxed than the GEDCOM spec allows.
 * 
 * @author frizbog
 * @since v3.0.1
 */
public class DateParser {

    /**
     * When a range or imprecise date value is found, what is the preference for handling it?
     */
    public enum ImpreciseDatePreference {
        /**
         * Return as precise a date as possible. For ranges and periods where more than one date is supplied (e.g., FROM 17 JUL 2016
         * TO 31 JUL 2016), use the first of the two dates.
         */
        PRECISE,

        /**
         * Return the earliest reasonable value for the interpreted date or range.
         */
        FAVOR_EARLIEST,

        /**
         * Return the latest reasonable value for the interpreted date or range.
         */
        FAVOR_LATEST,

        /**
         * Return the midpoint between the earliest and latest possible values for the interpreted date or range. For example, if a
         * value of "1900" is supplied, the value returned is 1900-07-01 (July 1, 1900). "JUL 1900" is supplied, the value returned
         * is 1900-07-15 (July 15). If the supplied value is not a range (i.e., there is only one date), return as precise a value
         * as possible.
         */
        FAVOR_MIDPOINT
    }

    /**
     * Miscellaneous date characters, for ignoring sections of dates - alphanumeric, spaces, and periods
     */
    private static final String FORMAT_DATE_MISC = "[A-Za-z0-9. ]*";

    /**
     * The regex string for a year
     */
    private static final String FORMAT_YEAR = "\\d{1,4}(\\/\\d{2})? ?(BC|B.C.|BCE)?";

    /**
     * Regex string for a day value
     */
    private static final String FORMAT_DAY = "(0?[1-9]|[12]\\d|3[01])";

    /**
     * Regex string for a month value
     */
    private static final String FORMAT_MONTH = "(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)";

    /**
     * Regex string for case insensitivity
     */
    private static final String FORMAT_CASE_INSENSITIVE = "(?i)";

    /**
     * The regex pattern that identifies a single, full date, with year, month, and day
     */
    static final Pattern PATTERN_SINGLE_DATE_FULL = Pattern.compile(FORMAT_CASE_INSENSITIVE + FORMAT_DAY + " " + FORMAT_MONTH + " "
            + FORMAT_YEAR);

    /**
     * The regex pattern that identifies a single date, with year, month, but no day
     */
    static final Pattern PATTERN_SINGLE_DATE_MONTH_YEAR = Pattern.compile(FORMAT_CASE_INSENSITIVE + FORMAT_MONTH + " "
            + FORMAT_YEAR);

    /**
     * The regex pattern that identifies a single date, year only (no month or day)
     */
    static final Pattern PATTERN_SINGLE_DATE_YEAR_ONLY = Pattern.compile(FORMAT_CASE_INSENSITIVE + FORMAT_YEAR);

    /**
     * The regex pattern that identifies two-date range or period
     */
    static final Pattern PATTERN_TWO_DATES = Pattern.compile(FORMAT_CASE_INSENSITIVE
            + "(FROM|BEF|BEF\\.|BET|BET\\.|BTW|BTW\\.|BETWEEN) " + FORMAT_DATE_MISC + FORMAT_YEAR + " (AND|TO) " + FORMAT_DATE_MISC
            + FORMAT_YEAR);

    /**
     * Parse the string as date, with the default imprecise date handling preference of {@link ImpreciseDatePreference#PRECISE}.
     * 
     * @param dateString
     *            the date string
     * @return the date, if one can be derived from the string
     */
    public Date parse(String dateString) {
        return parse(dateString, ImpreciseDatePreference.PRECISE);
    }

    /**
     * Parse the string as date
     * 
     * @param dateString
     *            the date string
     * @param pref
     *            the preference for handling an imprecise date.
     * @return the date, if one can be derived from the string
     */
    public Date parse(String dateString, ImpreciseDatePreference pref) {
        String ds = removeApproximations(dateString.toUpperCase());
        ds = removeOpenEndedRangesAndPeriods(ds);
        if (PATTERN_SINGLE_DATE_FULL.matcher(ds).matches()) {
            return getYearMonthDay(ds);
        }
        if (PATTERN_SINGLE_DATE_MONTH_YEAR.matcher(ds).matches()) {
            return getYearMonthNoDay(ds, pref);
        }
        if (PATTERN_SINGLE_DATE_YEAR_ONLY.matcher(ds).matches()) {
            return getYearOnly(ds, pref);
        }
        if (PATTERN_TWO_DATES.matcher(ds).matches()) {
            return getPreferredDateFromRangeOrPeriod(ds, pref);
        }
        return null;
    }

    /**
     * Format a string so BC dates are turned into negative years, for parsing by {@link SimpleDateFormat}
     * 
     * @param dateString
     *            the date string, which may or may not have a BC suffix
     * @return the date formatted so BC dates have negative years
     */
    String formatBC(String dateString) {
        String d = dateString;
        if (d.endsWith("BC") || d.endsWith("BCE") || d.endsWith("B.C.") || d.endsWith("B.C.E.")) {
            String ds = d.substring(0, d.lastIndexOf('B')).trim();
            String yyyy = null;
            if (ds.lastIndexOf(' ') > -1) {
                yyyy = ds.substring(ds.lastIndexOf(' ')).trim();
                int i = Integer.parseInt(yyyy);
                int bc = 1 - i;
                String ddMMM = ds.substring(0, ds.lastIndexOf(' '));
                d = ddMMM + " " + bc;
            } else {
                yyyy = ds.trim();
                int i = Integer.parseInt(yyyy);
                d = "" + (1 - i);
            }
        }
        return d;
    }

    /**
     * Return a version of the string with approximation prefixes removed, including handling for interpreted dates
     * 
     * @param dateString
     *            the date string
     * @return a version of the string with approximation prefixes removed
     */
    String removeApproximations(String dateString) {
        String ds = removePrefixes(dateString, new String[] { "ABT", "ABOUT", "APPX", "APPROX", "CAL", "CALC", "EST" });

        // Interpreted dates - require terms in parentheses after the date
        if (ds.startsWith("INT ") && ds.indexOf('(') > 8) {
            return ds.substring(4, ds.indexOf('(')).trim();
        }
        if (ds.startsWith("INT. ") && ds.indexOf('(') > 9) {
            return ds.substring(4, ds.indexOf('(')).trim();
        }

        return ds;
    }

    /**
     * Remove any of a set of prefixes from a date string. The prefixes will be removed if they begin the string, followed by an
     * optional period, then a space. For example, if "BEF" is one of the prefixes passed in, and the date string passed in is
     * either "BEF 1900" or "BEF. 1900", the result will be "1900".
     * 
     * @param dateString
     *            the date string
     * @param prefixes
     *            the prefixes
     * @return the string with the prefixes removed
     */
    String removePrefixes(String dateString, String[] prefixes) {

        for (String pfx : prefixes) {
            if (dateString.startsWith(pfx + " ") && dateString.length() > pfx.length() + 1) {
                return dateString.substring(pfx.length() + 1).trim();
            }
            if (dateString.startsWith(pfx + ". ") && dateString.length() > pfx.length() + 2) {
                return dateString.substring(pfx.length() + 2).trim();
            }
        }
        return dateString;
    }

    /**
     * Split a two-date string, removing prefixes, and return an array of two date strings
     * 
     * @param dateString
     *            the date string containing two dates
     * @param splitOn
     *            the delimiting phrase or character between the two dates
     * @return an array of two strings, or null if the supplied <code>dateString</code> value does not contain the
     *         <code>splitOn</code> delimiter value
     */
    String[] splitTwoDateString(String dateString, String splitOn) {
        if (dateString.contains(splitOn)) {
            String[] dateStrings = new String[2];
            dateStrings[0] = removePrefixes(dateString.substring(0, dateString.indexOf(splitOn)).trim(), new String[] { "BETWEEN",
                    "BET", "BTW", "FROM" });
            dateStrings[1] = dateString.substring(dateString.indexOf(splitOn) + splitOn.length()).trim();
            return dateStrings;
        }
        return null;
    }

    /**
     * Attempt to parse <code>dateString</code> using date format <code>fmt</code>. If successful, return the date. Otherwise return
     * null.
     * 
     * @param dateString
     *            the date string
     * @param pattern
     *            the date format to try parsing with
     * @return the date if successful, or null if the date cannot be parsed using the format supplied
     */
    private Date getDateWithFormatString(String dateString, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        try {
            return sdf.parse(dateString);
        } catch (@SuppressWarnings("unused") ParseException ignored) {
            return null;
        }
    }

    /**
     * Get the preferred date from a range or period
     * 
     * @param dateString
     *            the date string
     * @param pref
     *            the preferred method of handling the range
     * @return the date, or null if no date could be parsed from the data
     */
    private Date getPreferredDateFromRangeOrPeriod(String dateString, ImpreciseDatePreference pref) {
        // Split the string into two dates
        String[] dateStrings = splitTwoDateString(dateString, " AND ");
        if (dateStrings == null) {
            dateStrings = splitTwoDateString(dateString, " TO ");
        }
        if (dateStrings == null) {
            return null;
        }

        // Calculate the dates from the two strings, based on what's preferred
        switch (pref) {
            case FAVOR_EARLIEST:
                return parse(dateStrings[0], pref);
            case FAVOR_LATEST:
                return parse(dateStrings[1], pref);
            case FAVOR_MIDPOINT:
                Date d1 = parse(dateStrings[0], ImpreciseDatePreference.FAVOR_EARLIEST);
                Date d2 = parse(dateStrings[1], ImpreciseDatePreference.FAVOR_LATEST);
                long daysBetween = TimeUnit.DAYS.convert(d2.getTime() - d1.getTime(), TimeUnit.MILLISECONDS);
                Calendar c = Calendar.getInstance();
                c.setTime(d1);
                c.add(Calendar.DAY_OF_YEAR, (int) daysBetween / 2);
                Date result = c.getTime();
                return result;
            case PRECISE:
                return parse(dateStrings[0], pref);
            default:
                throw new IllegalArgumentException("Unexpected value for imprecise date preference: " + pref);
        }
    }

    /**
     * Get the date from a date string when the string is formatted with a month and year but no day
     * 
     * @param dateString
     *            the date string
     * @return the date found, if any, or null if no date could be extracted
     */
    private Date getYearMonthDay(String dateString) {
        return getDateWithFormatString(formatBC(dateString), "dd MMM yyyy");
    }

    /**
     * Get the date from a date string when the string is formatted with a month and year but no day
     * 
     * @param dateString
     *            the date string
     * @param pref
     *            preference on how to handle imprecise dates, like this one - return the earliest day of the month, the latest, the
     *            midpoint?
     * @return the date found, if any, or null if no date could be extracted
     */
    private Date getYearMonthNoDay(String dateString, ImpreciseDatePreference pref) {
        Date d = getDateWithFormatString(formatBC(dateString), "MMM yyyy");
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        switch (pref) {
            case FAVOR_EARLIEST:
                // First day of month
                c.set(Calendar.DAY_OF_MONTH, 1);
                return c.getTime();
            case FAVOR_LATEST:
                // Last day of month
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.add(Calendar.MONTH, 1);
                c.add(Calendar.DAY_OF_YEAR, -1);
                return c.getTime();
            case FAVOR_MIDPOINT:
                // Middle day of month
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.add(Calendar.MONTH, 1);
                c.add(Calendar.DAY_OF_MONTH, -1);
                int dom = c.get(Calendar.DAY_OF_MONTH) / 2;
                c.set(Calendar.DAY_OF_MONTH, dom);
                return c.getTime();
            case PRECISE:
                return d;
            default:
                throw new IllegalArgumentException("Unknown value for date handling preference: " + pref);
        }
    }

    /**
     * Get the date from a date string when the string is formatted with a year but no month or day
     * 
     * @param dateString
     *            the date string
     * @param pref
     *            preference on how to handle imprecise dates, like this one - return the earliest day of the month, the latest, the
     *            midpoint?
     * @return the date found, if any, or null if no date could be extracted
     */
    private Date getYearOnly(String dateString, ImpreciseDatePreference pref) {
        Date d = getDateWithFormatString(formatBC(dateString), "yyyy");
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        switch (pref) {
            case FAVOR_EARLIEST:
                // First day of year
                c.set(Calendar.DAY_OF_YEAR, 1);
                return c.getTime();
            case FAVOR_LATEST:
                // Last day of year
                c.set(Calendar.MONTH, Calendar.DECEMBER);
                c.set(Calendar.DAY_OF_MONTH, 31);
                return c.getTime();
            case FAVOR_MIDPOINT:
                // Middle day of year - go with July 1. Not precisely midpoint but feels midpointy.
                c.set(Calendar.MONTH, Calendar.JULY);
                c.set(Calendar.DAY_OF_MONTH, 1);
                return c.getTime();
            case PRECISE:
                return d;
            default:
                throw new IllegalArgumentException("Unknown value for date handling preference: " + pref);
        }
    }

    /**
     * Remove the prefixes for open ended date ranges with only one date (e.g., "BEF 1900", "FROM 1756", "AFT 2000")
     * 
     * @param dateString
     *            the date string
     * @return the same date string with range/period prefixes removed, but only if it's an open-ended period or range
     */
    private String removeOpenEndedRangesAndPeriods(String dateString) {
        if (!PATTERN_TWO_DATES.matcher(dateString).matches()) {
            return removePrefixes(dateString, new String[] { "FROM", "BEF", "BEFORE", "AFT", "AFTER", "TO" });
        }

        return dateString;
    }

}