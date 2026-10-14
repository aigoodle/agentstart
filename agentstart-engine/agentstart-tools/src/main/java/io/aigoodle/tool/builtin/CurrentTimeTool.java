package io.aigoodle.tool.builtin;

import io.aigoodle.tool.AbstractToolDefinition;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Returns the current date-time, optionally in a given IANA timezone
 * (argument {@code zone}, e.g. {@code Asia/Shanghai}).
 */
public class CurrentTimeTool extends AbstractToolDefinition {

    @Override
    public String name() {
        return "current_time";
    }

    @Override
    public String description() {
        return "Get the current date and time. Optional argument: 'zone' (IANA id, e.g. 'Asia/Shanghai').";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"zone\":{\"type\":\"string\"}}}";
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String zoneId = stringArgument(arguments, "zone", "UTC");
        try {
            ZonedDateTime currentTime = ZonedDateTime.now(ZoneId.of(zoneId));
            return currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        } catch (DateTimeException invalidZone) {
            return "error: invalid zone '" + zoneId + "'";
        }
    }
}
