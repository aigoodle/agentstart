package io.aigoodle.web.dto;

import java.util.List;
import lombok.Data;

/** Body payload for {@code POST/PUT /apps/{appId}/datasets}. */
@Data
public class AttachDatasetsRequest {
    /** Dataset ids to attach (POST) or the full replacement set (PUT). */
    private List<String> datasetIds;
}
