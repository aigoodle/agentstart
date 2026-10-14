package io.aigoodle.web.support;

import io.aigoodle.common.exception.PlatformException;

/** Resolves the application identity accepted by Dify-compatible endpoints. */
public final class DifyAppIdResolver {

    private DifyAppIdResolver() {}

    public static String resolve(String queryAppId, String headerAppId, String authorizationHeader) {
        String appId = firstNonBlank(queryAppId, headerAppId, bearerToken(authorizationHeader));
        if (appId == null) {
            throw new PlatformException(
                    "missing_app_id",
                    "Unable to identify the application; provide appId, X-App-Id, or Authorization",
                    null);
        }
        return appId;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String bearerToken(String authorizationHeader) {
        String header = trimToNull(authorizationHeader);
        if (header == null) {
            return null;
        }
        if (header.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return trimToNull(header.substring("Bearer ".length()));
        }
        return header;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String value = trimToNull(candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
