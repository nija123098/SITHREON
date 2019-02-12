package com.nija123098.sithreon.backend.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileUtilTest {

    @Test
    public void isLocalPath() {
        assertTrue(FileUtil.isLocalPath("/home/"));
        assertTrue(FileUtil.isLocalPath("C:\\Users\\"));
        assertTrue(FileUtil.isLocalPath("place/"));
        assertTrue(FileUtil.isLocalPath("location\\"));
        assertFalse(FileUtil.isLocalPath("http://example.com"));
    }
}
