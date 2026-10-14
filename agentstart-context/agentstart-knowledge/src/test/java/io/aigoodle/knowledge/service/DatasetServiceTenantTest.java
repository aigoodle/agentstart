package io.aigoodle.knowledge.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.knowledge.mapper.DatasetMapper;
import org.junit.jupiter.api.Test;

class DatasetServiceTenantTest {

    @Test
    void foreignDatasetCannotBeUpdatedOrDeletedThroughTenantScopedApi() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        DatasetService service = new DatasetService(mapper);

        assertThatThrownBy(() -> service.update("tenant-a", "dataset-b", new UpdateDatasetRequest()))
                .hasMessageContaining("Dataset not found");
        assertThatThrownBy(() -> service.delete("tenant-a", "dataset-b")).hasMessageContaining("Dataset not found");

        verify(mapper, never()).update(any(), any());
        verify(mapper, never()).delete(any());
    }
}
