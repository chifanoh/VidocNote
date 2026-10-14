package com.vidoc.service;

import com.vidoc.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummaryCursorTest {

    @Test
    void roundTripsInstantWithMicrosecondPrecision() {
        Instant createdAt = Instant.parse("2026-09-16T03:04:05.123456Z");
        String cursor = SummaryCursor.encode(createdAt, "0192f0aa-bbbb-7ccc-8ddd-eeeeeeeeeeee");

        SummaryCursor.Value value = SummaryCursor.decode(cursor);

        assertThat(value.createdAt()).isEqualTo(createdAt);
        assertThat(value.publicId()).isEqualTo("0192f0aa-bbbb-7ccc-8ddd-eeeeeeeeeeee");
    }

    @Test
    void keepsCursorOpaque() {
        String cursor = SummaryCursor.encode(Instant.parse("2026-09-16T03:04:05Z"), "0192f0aa-bbbb-7ccc-8ddd-eeeeeeeeeeee");

        assertThat(cursor).doesNotContain(":").doesNotContain("2026");
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> SummaryCursor.decode("这不是游标")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> SummaryCursor.decode("")).isInstanceOf(ApiException.class);
    }
}
