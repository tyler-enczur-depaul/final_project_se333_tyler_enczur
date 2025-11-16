package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import static org.junit.Assert.*;

public class JavaUnicodeEscaperTest {

    @Test
    public void testAsciiNotEscaped() {
        JavaUnicodeEscaper esc = JavaUnicodeEscaper.outsideOf(32, 0x7f);
        assertEquals("A", esc.translate("A"));
    }

    @Test
    public void testSurrogatePairEscaped() {
        JavaUnicodeEscaper esc = JavaUnicodeEscaper.outsideOf(32, 0x7f);
        // U+1F600 GRINNING FACE -> surrogate pair \uD83D\uDE00
        final String input = "\uD83D\uDE00";
        assertEquals("\\uD83D\\uDE00", esc.translate(input));
    }

    @Test
    public void testBetweenEscapes() {
        JavaUnicodeEscaper esc = JavaUnicodeEscaper.between(0, 0x7f);
        // codepoint 0 should be escaped as \u0000
        assertEquals("\\u0000", esc.translate("\u0000"));
    }

    @Test
    public void testHexFormattingBranches() {
        // cover >0xfff branch
        JavaUnicodeEscaper esc = JavaUnicodeEscaper.between(0, Integer.MAX_VALUE);
        assertEquals("\\u1234", esc.translate("\u1234"));
        // cover >0xff branch
        assertEquals("\\u0ABC", esc.translate("\u0ABC"));
        // cover >0xf branch
        assertEquals("\\u00BC", esc.translate("\u00BC"));
        // cover <=0xf branch (use 0x000F)
        assertEquals("\\u000F", esc.translate("\u000F"));
    }

    @Test
    public void testBoundarySpaceNotEscaped() {
        JavaUnicodeEscaper esc = JavaUnicodeEscaper.outsideOf(32, 0x7f);
        // space (0x20) is on the boundary and should not be escaped for outsideOf
        assertEquals(" ", esc.translate("\u0020"));
    }
}
