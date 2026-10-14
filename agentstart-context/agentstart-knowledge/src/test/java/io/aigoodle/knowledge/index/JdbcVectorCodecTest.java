package io.aigoodle.knowledge.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcVectorCodecTest {

    @Test
    void roundTripsAStoredVector() {
        float[] vector = {0.25f, -1.5f, 3.0f};

        assertThat(JdbcVectorCodec.decode(JdbcVectorCodec.encode(vector))).containsExactly(vector);
    }

    @Test
    void treatsMissingStorageAsAnEmptyVector() {
        assertThat(JdbcVectorCodec.decode(null)).isEmpty();
        assertThat(JdbcVectorCodec.decode("")).isEmpty();
    }
}
