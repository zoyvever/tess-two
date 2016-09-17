package com.example.npakudin;

import com.example.npakudin.testocr.micr.CheckData;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CheckDataTest {

    @Before
    public void setUp() {

    }

    @Test
    public void testConnectPaired() {

        checkCheck("a971907199a 9100712469c 0949",            "971907199 9100712469 0949");
        checkCheck("c000001031c a053101121a5112445821",       "053101121 5112445821 000001031"); // ! distinguish routing & check by leading zeros
        checkCheck("a123456789a 000123456789c 1001",          "123456789 000123456789 1001");
        checkCheck("c747313c a111001150a c00100363242c",      "111001150 00100363242 747313");
        checkCheck("c2001040882c a011500120a 20752113c",      "011500120 20752113 2001040882");
        checkCheck("c01403059c a041000124a 4000020309c",      "041000124 4000020309 01403059");
        checkCheck("a054000030a5501342773c21264",             "054000030 5501342773 21264");
        checkCheck("c000002971ca061000104a1000113301468c",    "061000104 1000113301468 000002971");  // ! distinguish routing & check by leading zeros
        checkCheck("a123454321a 0123454321c 9999",            "123454321 0123454321 9999");
        checkCheck("c1580743c a026013673a 4271497707c",       "026013673 4271497707 1580743");
        checkCheck("a271071321a c9080054103c 0903",           "271071321 9080054103 0903");
        checkCheck("c001163c a241070417a 4500199879c",        "241070417 4500199879 001163");
        checkCheck("c010750c a061300366a 100929453c",         "061300366 100929453 010750");
        checkCheck("a321370765a0101 12345d67890c",            "321370765 12345d67890 0101");
        checkCheck("a121000358a2417d02727d07119c",            "121000358 02727d07119 2417");
        checkCheck("a063100277a 003661702511c 1278",          "063100277 003661702511 1278");
        checkCheck("a122000661a0632d29768d06872c",            "122000661 29768d06872 0632");
        checkCheck("c00124520 c a061112788a c3299985345c",    "061112788 3299985345 00124520");
        checkCheck("c095405c a065400137a 1571658531c",        "065400137 1571658531 095405");
        checkCheck("c9049302603c a044000037a 758661425c",     "044000037 758661425 9049302603");
        checkCheck("c4946364c a031100209a 38657825c",         "031100209 38657825 4946364");
        checkCheck("c000022546c a031301422a362555855",        "031301422 362555855 000022546"); // ! distinguish routing & check by leading zeros
        checkCheck("c000469405c a021000021a230002410199c",    "021000021 230002410199 000469405"); // ! distinguish routing & check by leading zeros
        checkCheck("a129131673a0505 0114584906c",             "129131673 0114584906 0505");
        checkCheck("a044000804a 000469405 1004",              "044000804 000469405 1004");
        checkCheck("c5634207c a073000228a 0007075080c",       "073000228 0007075080 5634207");
        checkCheck("a122000496a 12345678c 0101",              "122000496 12345678 0101");
        checkCheck("c5634207c a073000228a 0007075080c",       "073000228 0007075080 5634207");
    }

    private void checkCheck(String src, String parsed) {

        for (String item : allReplacements(src)) {
            CheckData checkData = new CheckData();
            checkData.parseAndCheck(item);

            Assert.assertEquals("Replacement: " + item, parsed, String.format("%s %s %s", checkData.routingNumber, checkData.accountNumber,
                    checkData.checkNumber));
            Assert.assertEquals(true, checkData.isOk);
        }
    }

    @Test
    public void testAllReplacement() {

        Assert.assertArrayEquals(new String[] {"0a1c2", "0%1c2", "0a1%2", "0%1%2"}, allReplacements("0a1c2"));
        Assert.assertArrayEquals(new String[] {"0123"}, allReplacements("0123"));
        Assert.assertArrayEquals(new String[] {"a", "%"}, allReplacements("a"));
    }

    private String[] allReplacements(String src) {

        List<String> res = new ArrayList<>();

        char[] srcChars = src.toCharArray();
        char[] chars = src.toCharArray();

        List<Integer> indices = new ArrayList<>();
        for (int i=0; i<chars.length; i++) {
            if (!("" + chars[i]).matches("[0-9 ]")) {
                indices.add(i);
            }
        }

        for (int i=0; i<Math.pow(2, indices.size()); i++) {

            for (int j=0; j<indices.size(); j++) {
                int curBitValue = (i >> j) & 0x1;
                chars[ indices.get(j) ] = (curBitValue == 1) ? '%' : srcChars[ indices.get(j) ];
            }
            res.add(new String(chars));
        }

        return res.toArray(new String[res.size()]);
    }
}