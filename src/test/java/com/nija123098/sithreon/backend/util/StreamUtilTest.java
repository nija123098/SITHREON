package com.nija123098.sithreon.backend.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class StreamUtilTest {

    @Test
    public void readFully() throws IOException {
        assertEquals("abcdef", StreamUtil.readFully(new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8)), 6));
        assertEquals("abcdef", StreamUtil.readFully(new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8)), 1));// test buffer being too short
    }
}
