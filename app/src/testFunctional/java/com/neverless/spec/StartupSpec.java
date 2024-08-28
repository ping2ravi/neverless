package com.neverless.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class StartupSpec extends FunctionalSpec {
    protected StartupSpec(ApplicationContext application) {
        super(application);
    }

    @Test
    void should_start_application_and_serve_healthcheck() {
        // when
        final var response = when().get("/healthcheck").thenReturn();

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().asString()).isEqualTo("OK");
    }
}
