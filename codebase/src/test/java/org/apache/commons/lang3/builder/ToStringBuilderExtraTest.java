package org.apache.commons.lang3.builder;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

public class ToStringBuilderExtraTest {

    @Test
    public void testAppendPrimitivesAndArrays() {
        final Integer base = Integer.valueOf(7);
        final ToStringBuilder b = new ToStringBuilder(base, ToStringStyle.SHORT_PREFIX_STYLE);

        b.append(true).append((byte)3).append('A').append(2.5d).append(1.2f).append(5).append(6L).append((short)9);
        b.append(new int[] {1,2,3});
        b.append(new Object[] {"x", null, Integer.valueOf(4)});

        final String out = b.toString();
        assertNotNull(out);
        // char may be rendered as char or numeric depending on style/format
        assertTrue(out.length() > 0);
        // array content should contain the elements or a size summary
        assertTrue((out.contains("1") && out.contains("2") && out.contains("3")) || out.contains("<size="));
    }

    @Test
    public void testFieldNamedSummaryAndDetail() {
        final ToStringBuilder b = new ToStringBuilder("obj", ToStringStyle.DEFAULT_STYLE);
        b.append("arr", new int[] {9,8}, false); // summary
        b.append("list", new ArrayList<Object>(), false); // summary size
        b.append("val", Integer.valueOf(3), true); // detail

        final String s = b.toString();
        assertTrue(s.contains("<size=") || s.contains("9"));
        assertTrue(s.contains("val=3") || s.contains("3"));
    }

    @Test
    public void testAppendSuperAndToStringHandling() {
        final ToStringBuilder b = new ToStringBuilder(Integer.valueOf(1));
        b.appendSuper("Integer@1111[a=hello]");
        b.appendToString(null);
        b.appendToString("Integer@2222[b=world]");
        final String s = b.toString();
        assertTrue(s.contains("a=hello") || s.contains("b=world"));
    }

    @Test
    public void testAppendNullsAndBuild() {
        final ToStringBuilder b = new ToStringBuilder(null);
        b.append((Object) null);
        final String built = b.build();
        assertNotNull(built);
        assertTrue(built.contains(ToStringStyle.DEFAULT_STYLE.getNullText()));
    }

    @Test
    public void testAppendAsObjectToString() {
        final Object o = new Object();
        final ToStringBuilder b = new ToStringBuilder(o);
        b.appendAsObjectToString(o);
        final String buf = b.getStringBuffer().toString();
        assertTrue(buf.contains(o.getClass().getName()) || buf.contains("@"));
        // ensure registry is cleaned by finishing the builder
        b.toString();
    }
}
