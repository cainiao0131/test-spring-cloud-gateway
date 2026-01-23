package com.cainiao.gateway.util;

import lombok.experimental.UtilityClass;

import java.net.URI;
import java.net.URISyntaxException;

@UtilityClass
public class HttpUtil {

    public static URI buildURI(String scheme, String host, Integer port, String path) {
        if (path == null) {
            path = "";
        }
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        try {
            return port != null && port > 0 && port != getDefaultPort(scheme)
                ? new URI(scheme, null, host, port, path, null, null)
                : new URI(scheme, host, path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static int getDefaultPort(String scheme) {
        return switch (scheme.toLowerCase()) {
            case "http" -> 80;
            case "https" -> 443;
            case "ftp" -> 21;
            default -> -1;
        };
    }
}
