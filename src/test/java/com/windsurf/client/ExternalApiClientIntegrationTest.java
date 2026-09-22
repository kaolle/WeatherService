package com.windsurf.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.windsurf.model.DifficultyLevel;
import com.windsurf.model.SpotEntity;
import com.windsurf.model.SpotSource;
import com.windsurf.model.SpotType;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(ExternalApiWireMockResource.class)
class ExternalApiClientIntegrationTest {

    @BeforeEach
    void resetWireMock() {
        wireMock().resetAll();
    }

    @Test
    void discoverEndpointUsesQuarkusServiceAndWireMockOverpass() {
        wireMock().stubFor(post(urlEqualTo("/api/interpreter"))
            .withRequestBody(containing("nwr"))
            .willReturn(okJson("{\"elements\": []}")));

        given()
            .queryParam("south", 39.5)
            .queryParam("west", 2.6)
            .queryParam("north", 39.7)
            .queryParam("east", 2.8)
        .when()
            .get("/spots/discover")
        .then()
            .statusCode(200)
            .body("spots", hasSize(0));

        wireMock().verify(postRequestedFor(urlEqualTo("/api/interpreter")));
    }

    @Test
    void topEndpointUsesMongoFixtureAndWireMockOpenMeteo() {
        SpotEntity.deleteAll();
        SpotEntity spot = new SpotEntity();
        spot.externalId = "wiremock-top-spot";
        spot.name = "WireMock Bay";
        spot.latitude = 58.0;
        spot.longitude = 16.5;
        spot.region = "Testregion";
        spot.type = SpotType.FLAT_WATER;
        spot.difficulty = DifficultyLevel.BEGINNER;
        spot.minWindSpeed = 4.0;
        spot.idealWindSpeed = 6.0;
        spot.maxWindSpeed = 12.0;
        spot.bestDirections = java.util.List.of("W");
        spot.source = SpotSource.USER;
        spot.approved = true;
        spot.persist();

        wireMock().stubFor(get(urlPathEqualTo("/v1/forecast"))
            .withQueryParam("latitude", com.github.tomakehurst.wiremock.client.WireMock.equalTo("58.0"))
            .withQueryParam("longitude", com.github.tomakehurst.wiremock.client.WireMock.equalTo("16.5"))
            .willReturn(okJson("""
                {
                  "current": {
                    "wind_speed_10m": 21.6,
                    "wind_direction_10m": 270,
                    "wind_gusts_10m": 28.8
                  }
                }
                """)));

        given()
            .queryParam("lat", 58.0)
            .queryParam("lon", 16.5)
            .queryParam("radius_km", 10)
            .queryParam("limit", 10)
        .when()
            .get("/spots/top")
        .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].spot.name", equalTo("WireMock Bay"))
            .body("[0].currentWind.speedMs", equalTo(6.0f))
            .body("[0].currentWind.directionLabel", equalTo("W"));

        wireMock().verify(getRequestedFor(urlPathEqualTo("/v1/forecast")));
    }

    @Test
    void openMeteoClientSendsForecastParametersAndParsesWindResponse() {
        wireMock().stubFor(get(urlPathEqualTo("/v1/forecast"))
            .withQueryParam("latitude", com.github.tomakehurst.wiremock.client.WireMock.equalTo("58.123"))
            .withQueryParam("longitude", com.github.tomakehurst.wiremock.client.WireMock.equalTo("16.456"))
            .withQueryParam("current", com.github.tomakehurst.wiremock.client.WireMock.equalTo("wind_speed_10m,wind_direction_10m,wind_gusts_10m"))
            .willReturn(okJson("""
                {
                  "current": {
                    "wind_speed_10m": 18.0,
                    "wind_direction_10m": 245,
                    "wind_gusts_10m": 27.0
                  }
                }
                """)));

        OpenMeteoClient client = client(OpenMeteoClient.class);
        OpenMeteoResponse response = client.getCurrent(
            58.123, 16.456,
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m");

        assertNotNull(response);
        assertNotNull(response.current());
        assertEquals(18.0, response.current().windSpeed());
        assertEquals(245.0, response.current().windDirection());
        assertEquals(27.0, response.current().windGusts());
        wireMock().verify(getRequestedFor(urlPathEqualTo("/v1/forecast")));
    }

    @Test
    void overpassClientPostsQueryAndParsesElements() {
        wireMock().stubFor(post(urlEqualTo("/api/interpreter"))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withRequestBody(containing("data="))
            .willReturn(okJson("""
                {
                  "elements": [
                    {
                      "type": "node",
                      "id": 123,
                      "lat": 39.55,
                      "lon": 2.69,
                      "tags": {"name": "Palma spot", "sport": "surfing"}
                    }
                  ]
                }
                """)));

        OverpassClient client = client(OverpassClient.class);
        OverpassResponse response = client.query("[out:json];node(39.5,2.6,39.6,2.7);out body;");

        assertNotNull(response);
        assertEquals(1, response.elements().size());
        assertEquals("node", response.elements().get(0).type());
        assertEquals(123L, response.elements().get(0).id());
        assertEquals(39.55, response.elements().get(0).lat());
        assertEquals("Palma spot", response.elements().get(0).tags().get("name"));
        wireMock().verify(postRequestedFor(urlEqualTo("/api/interpreter")));
    }

    @Test
    void overpassDiscoveryClientParsesCenterCoordinatesAndRemark() {
        wireMock().stubFor(post(urlEqualTo("/api/interpreter"))
            .willReturn(okJson("""
                {
                  "remark": "runtime error: partial result",
                  "elements": [
                    {
                      "type": "way",
                      "id": 456,
                      "center": {"lat": 39.60, "lon": 2.70},
                      "tags": {"name": "Way spot", "sport": "kitesurfing"}
                    }
                  ]
                }
                """)));

        OverpassDiscoveryClient client = client(OverpassDiscoveryClient.class);
        OverpassDiscoveryClient.Result response = client.query(
            "[out:json][timeout:12];nwr[\"sport\"](39.5,2.6,39.7,2.8);out center 100;");

        assertNotNull(response);
        assertEquals("runtime error: partial result", response.remark());
        assertEquals(1, response.elements().size());
        var element = response.elements().get(0);
        assertEquals("way", element.type());
        assertEquals(456L, element.id());
        assertEquals(39.60, element.center().lat());
        assertEquals(2.70, element.center().lon());
        assertEquals("Way spot", element.tags().get("name"));
        wireMock().verify(postRequestedFor(urlEqualTo("/api/interpreter")));
    }

    private WireMockServer wireMock() {
        return ExternalApiWireMockResource.server();
    }

    private <T> T client(Class<T> clientType) {
        return QuarkusRestClientBuilder.newBuilder()
            .baseUri(URI.create(wireMock().baseUrl()))
            .build(clientType);
    }
}
