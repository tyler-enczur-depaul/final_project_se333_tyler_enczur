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
}
