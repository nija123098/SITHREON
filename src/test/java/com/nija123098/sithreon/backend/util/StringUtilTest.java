package com.nija123098.sithreon.backend.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class StringUtilTest {

    @Test
    public void removeRepeats() {
        assertEquals("abcdef", StringUtil.removeRepeats("abcdef", ' '));
        assertEquals("abcdef", StringUtil.removeRepeats("abcdef", 'a'));
        assertEquals("abbbbc", StringUtil.removeRepeats("abbbbc", 'a'));
        assertEquals("abbbbcdeff", StringUtil.removeRepeats("abbbbcddeff", 'd'));
        assertEquals("abcbeb", StringUtil.removeRepeats("abbbbcbbebb", 'b'));
    }

    @Test
    public void join() {
        assertEquals("abcd", StringUtil.join("", "a", "b", "c", "d"));
        assertEquals("a-b-c-d", StringUtil.join("-", "a", "b", "c", "d"));
        assertEquals("a-b-c-d-", StringUtil.join("-", "a", "b", "c", "d", ""));
    }

    @Test
    public void endAt() {
        assertEquals("", StringUtil.endAt("abc", ""));
        assertEquals("a", StringUtil.endAt("abc", "bc"));
        assertEquals("a", StringUtil.endAt("abcbc", "bc"));
        assertEquals("a", StringUtil.endAt("abcbc", "bc"));
        assertEquals("a", StringUtil.endAt("abcdbc", "bc"));
    }

    @Test
    public void base64EncodeOneLine() {// check for no change, tested method utilizes JDK
        assertEquals("AA==", StringUtil.base64EncodeOneLine(new byte[]{0}));
    }

    @Test
    public void getSha256Hash() {// check for no change, tested method utilizes JDK
        assertArrayEquals(new byte[]{-29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36, 39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85}, StringUtil.getSha256Hash(""));
    }
}
