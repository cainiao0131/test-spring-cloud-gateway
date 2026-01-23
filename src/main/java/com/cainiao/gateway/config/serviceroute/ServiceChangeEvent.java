package com.cainiao.gateway.config.serviceroute;

public record ServiceChangeEvent(String source, String systemName,
                                 String serviceName, Integer port, ChangeType changeType) {

    public enum ChangeType {
        ADD,
        MODIFY,
        DELETE,
    }
}
