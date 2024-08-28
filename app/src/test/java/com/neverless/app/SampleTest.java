package com.neverless.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SampleTest {
    @Test
    void should_act_as_a_sample() throws Exception {
        assertThat(1 + 1).isEqualTo(2);
    }
}
