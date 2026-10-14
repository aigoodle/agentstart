package io.aigoodle.web.controller;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.model.video.VideoModelService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnBean(VideoModelService.class)
@RequestMapping("/video-models")
public class VideoModelController {
    private final VideoModelService videos;

    public VideoModelController(VideoModelService videos) {
        this.videos = videos;
    }

    @PostMapping("/capabilities")
    public Map<String, Object> capabilities(@RequestBody Map<String, Object> selection) {
        return videos.capabilities(UserContextHolder.currentTenantId(), selection);
    }
}
