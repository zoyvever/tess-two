package com.example.npakudin.testocr.micr;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckData {

    public boolean isOk;
    public int distance = -1;
    public String realText = "";
    public Bitmap res;
    public String wholeText = "";
    public String routingNumber = "";
    public String accountNumber = "";
    public String checkNumber = "";
    public double minConfidence = 0;
    public double confidence = 0;
    public List<Symbol> symbols = null;
    public String filename;
    public String descr;
    public String errorMessage;

    public CheckData() {

    }

    public CheckData(Bitmap res, String wholeText, List<Symbol> symbols, double confidence) {
        this.res = res;
        this.wholeText = wholeText;
        this.symbols = symbols;
        this.confidence=confidence;

        parseAndCheck(wholeText);
    }

    public void parseAndCheck(String str) {

        parseRoutingNumber(str);

        // TODO: check routing checksum
        char[] d = routingNumber.toCharArray();
        for (int i=0; i<d.length; i++) {
            int x = (d[i] - (int)'0');
            d[i] = (char)x;
        }
        if (d.length != 9) {
            routingNumber = null;
            errorMessage += ", routing number length is invalid";
        } else {
            if ((3*(d[0]+d[3]+d[6])+7*(d[1]+d[4]+d[7])+(d[2]+d[5]+d[8])) % 10 == 0) {
                // ok
            } else {
                routingNumber = null;
                errorMessage += ", routing number checksum is invalid";
            }
        }


        this.isOk = routingNumber != null && accountNumber != null && checkNumber != null;
        if (errorMessage != null && errorMessage.length() > 2) {
            errorMessage = errorMessage.substring(2);
        }
    }

    private void parseRoutingNumber(String src) {

        // main algorithm:
        // 1. check: ([ c%])(\d{0,5})$
        // 2. acc: ([acd% ])([0-9d%]{8,13})[c%]?$
        // 3. routing: [a](\d{9})[a%]   |   [a%](\d{9})[a%]
        // 4. if routing gives 2 and  both with % only - use with less leading zeros
        // 5. check: ^[abcd% ]*(\d+)[abcd% ]*$


        Pair<String, String> pair;

        // 1. try to find check number at the end
        pair = parse(src, Pattern.compile("([ c%])(\\d{0,5})$"), "$1", 2);
        if (pair.first != null && pair.first.length() > 0) {
            checkNumber = pair.first;
            src = pair.second;
        } else {
            // not found - it's OK here
        }

        // 2. find account number
        pair = parse(src, Pattern.compile("([acd% ])([0-9d%]{8,13})[c%]?$"), "$1", 2);
        if (pair.first != null && pair.first.length() > 0) {
            // at the begin and end - "c", in the middle can be "d"
            accountNumber = pair.first.replaceAll("^%", "").replaceAll("%$", "").replace("%", "d");
            src = pair.second;
        } else {
            // not found - failure
            errorMessage += ", cannot find account number";
            return;
        }

        // 3. routing: [a%](\d{9})[a%]
        pair = parse(src, Pattern.compile("a(\\d{9})[a%]"), " ", 1);
        if (pair.first != null && pair.first.length() > 0) {
            routingNumber = pair.first;
            src = pair.second;
        } else {
            // have to separate one regex because I need access to matched group by single number
            pair = parse(src, Pattern.compile("%(\\d{9})a"), " ", 1);
            if (pair.first != null && pair.first.length() > 0) {
                routingNumber = pair.first;
                src = pair.second;
            } else {

                // not found - try fuzzy
                Pattern pattern = Pattern.compile("%(\\d{9})%");
                List<MatchResult> allMatches = allMatches(pattern, src);

                if (allMatches.size() == 1) {
                    // ok
                    routingNumber = allMatches.get(0).group(1);
                    src = pattern.matcher(src).replaceFirst(" ").trim();
                } else if (allMatches.size() == 2) {

                    // 4. if routing gives 2 and  both with % only - use with less leading zeros
                    String first = allMatches.get(0).group().replace("%", "");
                    String second = allMatches.get(1).group().replace("%", "");

                    if (leadingZerosCount(first) < leadingZerosCount(second)) {
                        routingNumber = first;
                        checkNumber = second;
                    } else if (leadingZerosCount(first) > leadingZerosCount(second)) {
                        checkNumber = first;
                        routingNumber = second;
                    } else {
                        // equals - failure
                        errorMessage += ", cannot distinguish check number & account number";
                        return;
                    }

                    // all numbers are found
                    return;

                } else {
                    errorMessage += ", cannot find routing number";
                    return;
                }
            }
        }

        // 5. 2nd attempt for check
        pair = parse(src, Pattern.compile("^[abcd% ]*(\\d+)[abcd% ]*$"), "$1", 1);
        if (pair.first != null && pair.first.length() > 0) {
            checkNumber = pair.first;
            src = pair.second;
        } else {
            // not found - failure
            errorMessage += ", cannot find check number";
            return;
        }
    }

    public static int leadingZerosCount(String str) {
        for (int i=0; i<str.length(); i++){
            if (str.charAt(i) != '0') {
                return i;
            }
        }
        return str.length();
    }

    private Pair<String, String> parse(String src, Pattern exact, String replacePattern, int extractGroup) {

        String parsed = null;

        Matcher matcher = exact.matcher(src);
        List<MatchResult> allMatches = new ArrayList<>();
        while (matcher.find()) {
            allMatches.add(matcher.toMatchResult());
        }
        if (allMatches.size() == 1) {
            parsed = allMatches.get(0).group(extractGroup);
            src = matcher.replaceFirst(replacePattern).trim();
        } else if (allMatches.size() > 1) {
            src = null;
        }

        return new Pair<>(parsed, src);
    }

    public static List<MatchResult> allMatches(final Pattern p, final CharSequence input) {

        List<MatchResult> res = new ArrayList<>();
        for (MatchResult item : allMatchesIterable(p, input)) {
            res.add(item);
        }
        return res;
    }

    public static Iterable<MatchResult> allMatchesIterable(final Pattern p, final CharSequence input) {
        return new Iterable<MatchResult>() {
            public Iterator<MatchResult> iterator() {
                return new Iterator<MatchResult>() {
                    // Use a matcher internally.
                    final Matcher matcher = p.matcher(input);
                    // Keep a match around that supports any interleaving of hasNext/next calls.
                    MatchResult pending;

                    public boolean hasNext() {
                        // Lazily fill pending, and avoid calling find() multiple times if the
                        // clients call hasNext() repeatedly before sampling via next().
                        if (pending == null && matcher.find()) {
                            pending = matcher.toMatchResult();
                        }
                        return pending != null;
                    }

                    public MatchResult next() {
                        // Fill pending if necessary (as when clients call next() without
                        // checking hasNext()), throw if not possible.
                        if (!hasNext()) { throw new NoSuchElementException(); }
                        // Consume pending so next call to hasNext() does a find().
                        MatchResult next = pending;
                        pending = null;
                        return next;
                    }

                    /** Required to satisfy the interface, but unsupported. */
                    public void remove() { throw new UnsupportedOperationException(); }
                };
            }
        };
    }

    public static void main(String[] args) {

        Pattern checkEndingPattern = Pattern.compile("([ c%])(\\d{0,5})$"); // get $2

        String res = checkEndingPattern.matcher("%971907194% 9100712469% 0949").replaceAll("#\\1");

        System.out.print("Hello " + res);
    }

//    public String findPattern(String pattern, String replace) {
//        String s = "UNRECOGNIZED";
//        Pattern pat = Pattern.compile(pattern);
//        Matcher m = pat.matcher(toCut);
//
//        while (m.find()) {
//            s = m.group().replace(replace, "");
//        }
//
//        toCut = toCut.replaceAll(s, " ");
//        return s;
//    }
}