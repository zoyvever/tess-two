package com.example.npakudin.testocr.recognition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckData {

    public String routingNumber = "";
    public String accountNumber = "";
    public String checkNumber = "";

    public boolean isOk;
    public String rawText = "";

    public double minConfidence = 0;
    public double confidence = 0;
    public String errorMessage;

    public CheckData() {

    }

    public CheckData(String rawText, double confidence) {
        this.rawText = rawText;
        this.confidence=confidence;

        parseAndCheck(rawText);
    }

    public void parseAndCheck(String str) {

        parseCheck(str);
        checkRoutingNumberChecksum();

        this.isOk = routingNumber != null && accountNumber != null && checkNumber != null;
        if (errorMessage != null && errorMessage.length() > 2) {
            errorMessage = errorMessage.substring(2);
        }
    }

    private void checkRoutingNumberChecksum() {

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
    }

    private void parseCheck(String src) {

        // main algorithm:
        // 1. check: ([ c])(\d{0,5})$
        // 2. acc: ([acd ])([0-9d]{8,13})c?$
        // 3. routing: a(\d{9})a
        // 4. 2nd attempt for check: ^[abcd ]*(\d+)[abcd ]*$

        Pair<String, String> pair;

        // 1. try to find check number at the end
        pair = parse(src, Pattern.compile("([ c])(\\d{0,5})$"), "$1", 2);
        if (pair.first != null && pair.first.length() > 0) {
            checkNumber = pair.first;
            src = pair.second;
        } else {
            // not found - it's OK here
        }

        // 2. find account number
        pair = parse(src, Pattern.compile("([acd ])([0-9d]{8,13})c?$"), "$1", 2);
        if (pair.first != null && pair.first.length() > 0) {
            // in the middle can be "d"
            accountNumber = pair.first;
            src = pair.second;
        } else {
            // not found - failure
            errorMessage += ", cannot find account number";
            return;
        }

        // 3. routing: a(\d{9})a
        pair = parse(src, Pattern.compile("a(\\d{9})a"), " ", 1);
        if (pair.first != null && pair.first.length() > 0) {
            routingNumber = pair.first;
            src = pair.second;
        } else {
            errorMessage += ", cannot find routing number";
            return;
        }

        // 4. 2nd attempt for check
        if (checkNumber == null || checkNumber.trim().length() == 0) {

            pair = parse(src, Pattern.compile("^[abcd ]*(\\d+)[abcd ]*$"), "$1", 1);
            if (pair.first != null && pair.first.length() > 0) {
                checkNumber = pair.first;
                src = pair.second;
            } else {
                // not found - failure
                errorMessage += ", cannot find check number";
                return;
            }
        }
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
}