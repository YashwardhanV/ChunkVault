package com.chunkvault.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HashingTest {
    @Test
    void computesKnownSha256Vector() {
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        assertThat(Hashing.sha256(input, input.length))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
