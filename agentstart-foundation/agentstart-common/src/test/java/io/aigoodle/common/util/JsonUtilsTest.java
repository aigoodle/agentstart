package io.aigoodle.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

    @Test
    void preservesTheDocumentedEmptyInputConventions() {
        assertThat(JsonUtils.parse(null, Sample.class)).isNull();
        assertThat(JsonUtils.parse("  ", new TypeReference<List<Sample>>() {})).isNull();
        assertThat(JsonUtils.parseMap("")).isEmpty();
        assertThat(JsonUtils.parseList(null, Sample.class)).isEmpty();
        assertThat(JsonUtils.readTree(" ")).isNull();
    }

    @Test
    void describesTheRequestedTargetWhenJsonCannotBeParsed() {
        assertThatThrownBy(() -> JsonUtils.parse("not-json", Sample.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to parse JSON into Sample")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);

        assertThatThrownBy(() -> JsonUtils.parseList("{}", Sample.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to parse JSON into List<Sample>");
    }

    @Test
    void roundTripsTypedValuesAndGenericCollections() {
        Sample source = new Sample("Ada", 37);

        String json = JsonUtils.toJson(source);

        assertThat(JsonUtils.parse(json, Sample.class)).isEqualTo(source);
        Map<String, Sample> samples =
                JsonUtils.parse("{\"primary\":" + json + "}", new TypeReference<Map<String, Sample>>() {});
        assertThat(samples).containsEntry("primary", source);
    }

    record Sample(String name, int age) {}
}
