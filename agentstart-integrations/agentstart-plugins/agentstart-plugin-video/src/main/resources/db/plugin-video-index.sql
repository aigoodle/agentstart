-- Apply once with your migration tool when automatic schema initialization is disabled.
CREATE INDEX idx_plugin_video_poll ON plugin_video_task (status, next_poll_at);
