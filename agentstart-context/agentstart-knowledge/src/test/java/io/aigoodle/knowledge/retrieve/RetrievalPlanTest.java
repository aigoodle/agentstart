package io.aigoodle.knowledge.retrieve;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.knowledge.config.RetrievalConfig;
import org.junit.jupiter.api.Test;

class RetrievalPlanTest {

    @Test
    void resolvesVectorRecallPoolAndRequestOverrides() {
        RetrievalConfig config = RetrievalConfig.vector();
        config.setTopK(5);
        config.setRecallMultiplier(4);
        RetrievalRequest request =
                RetrievalRequest.builder().topK(3).scoreThreshold(0.25).build();

        RetrievalPlan plan = RetrievalPlan.resolve(config, request);

        assertThat(plan.topK()).isEqualTo(3);
        assertThat(plan.recallLimit()).isEqualTo(12);
        assertThat(plan.scoreThreshold()).isEqualTo(0.25);
    }

    @Test
    void rerankPoolControlsRecallLimit() {
        RetrievalConfig config = RetrievalConfig.vector();
        config.setRerankEnabled(true);
        config.setRerankPoolSize(30);

        assertThat(RetrievalPlan.resolve(config, RetrievalRequest.builder().build())
                        .recallLimit())
                .isEqualTo(30);
    }
}
