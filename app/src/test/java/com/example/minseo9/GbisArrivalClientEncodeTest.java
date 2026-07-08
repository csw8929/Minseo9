package com.example.minseo9;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GbisArrivalClientEncodeTest {

    @Test
    public void encode_leavesAlphanumericUnchanged() {
        assertEquals("abc123", GbisArrivalClient.encode("abc123"));
    }

    @Test
    public void encode_percentEncodesSpaceAndAmpersand() {
        assertEquals("a+b%26c", GbisArrivalClient.encode("a b&c"));
    }

    @Test
    public void encode_percentEncodesUnicode() {
        assertEquals("%EB%8F%99%EC%B2%9C", GbisArrivalClient.encode("동천"));
    }
}
