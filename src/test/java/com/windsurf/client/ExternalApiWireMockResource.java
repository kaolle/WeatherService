package com.windsurf.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class ExternalApiWireMockResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        String baseUrl = server.baseUrl();
        return Map.of(
            "quarkus.rest-client.open-meteo.url", baseUrl,
            "quarkus.rest-client.overpass.url", baseUrl,
            "quarkus.rest-client.overpass-discovery.url", baseUrl
        );
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    static WireMockServer server() {
        if (server == null) throw new IllegalStateException("WireMock is not running");
        return server;
    }
}
