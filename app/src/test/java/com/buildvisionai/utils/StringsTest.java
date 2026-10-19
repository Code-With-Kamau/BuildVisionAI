package com.buildvisionai.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class StringsTest {
    @Test public void joinsWithTheSeparatorAndNoTrailingOne() {
        assertEquals("a; b; c", Strings.join("; ", Arrays.asList("a", "b", "c")));
        assertEquals("only", Strings.join("; ", Collections.singletonList("only")));
        assertEquals("", Strings.join("; ", new ArrayList<String>()));
        assertEquals("1-2", Strings.join("-", Arrays.asList(new StringBuilder("1"), "2")));
    }
}
