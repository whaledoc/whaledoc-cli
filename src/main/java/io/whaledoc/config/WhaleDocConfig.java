package io.whaledoc.config;

import lombok.Builder;

@Builder
public record WhaleDocConfig(String cliId, String accessToken) {
}
