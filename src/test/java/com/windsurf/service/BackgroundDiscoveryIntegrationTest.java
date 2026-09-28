package com.windsurf.service;

import com.windsurf.client.ExternalApiWireMockResource;
import com.windsurf.model.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(ExternalApiWireMockResource.class)
class BackgroundDiscoveryIntegrationTest {
    @Inject BackgroundDiscoveryService service;

    @BeforeEach void reset() {
        SpotEntity.deleteAll();
        ViewportInterestEntity.deleteAll();
        ImportedAreaEntity.deleteAll();
        ChangeLogEntity.deleteAll();
        var server = ExternalApiWireMockResource.server();
        server.resetAll();
        server.stubFor(get(urlPathEqualTo("/reverse"))
            .willReturn(okJson("{\"address\":{\"country\":\"Testland\"}}")));
    }

    @Test void importsNodesAndWaysThenApprovesCorrectedPositionWithoutDuplicates() {
        var server = ExternalApiWireMockResource.server();
        server.stubFor(post(urlEqualTo("/api/interpreter")).willReturn(okJson("""
            {"elements":[
              {"type":"node","id":101,"lat":58.6,"lon":17.6,"tags":{"name":"Node beach"}},
              {"type":"way","id":102,"center":{"lat":58.7,"lon":17.7},"tags":{"name":"Way beach"}},
              {"type":"way","id":103,"tags":{"name":"Missing position"}}
            ]}
            """)));
        Tile tile = new Tile(58.5, 17.5);
        service.importTile(tile);
        assertEquals(2, SpotEntity.count());
        SpotEntity way = SpotEntity.find("externalId", "osm-way-102").firstResult();
        assertEquals(58.7, way.latitude);
        assertEquals(17.7, way.longitude);
        assertFalse(way.approved);
        ImportedAreaEntity area = ImportedAreaEntity.findById(tile.key());
        assertEquals("SUCCESS", area.status);
        assertEquals(2, area.spotsFound);
        given().queryParam("south",58).queryParam("west",17).queryParam("north",59).queryParam("east",18)
            .get("/spots/pending").then().statusCode(200).body("size()",equalTo(2));
        service.importTile(tile);
        assertEquals(2, SpotEntity.count());

        Map<String,Object> body = approval();
        given().contentType("application/json").body(body).post("/spots/osm-way-102/approve")
            .then().statusCode(401);
        body.put("latitude", 91);
        approve(body,400);
        body.put("latitude",58.75);
        body.remove("longitude");
        approve(body,400);
        way = SpotEntity.find("externalId", "osm-way-102").firstResult();
        assertFalse(way.approved);
        assertEquals(58.7, way.latitude);
        body.put("longitude",17.75);
        approve(body,200);
        way = SpotEntity.find("externalId", "osm-way-102").firstResult();
        assertTrue(way.approved);
        assertEquals(58.75,way.latitude);
        assertEquals(17.75,way.longitude);
        assertEquals(1, SpotEntity.find("approved",false).count());
        ChangeLogEntity log = ChangeLogEntity.find("spotExternalId","osm-way-102").firstResult();
        assertTrue(log.changeJson.contains("58.75"));
    }

    private Map<String,Object> approval() {
        return new LinkedHashMap<>(Map.of("type","FLAT_WATER","difficulty","INTERMEDIATE",
            "bestDirections",java.util.List.of("W"),"minWindSpeed",5,"idealWindSpeed",8,
            "maxWindSpeed",15,"latitude",58.75,"longitude",17.75));
    }

    private void approve(Map<String,Object> body,int status) {
        given().header("X-Api-Key","test-only").header("X-User-Name","test-user")
            .contentType("application/json").body(body).post("/spots/osm-way-102/approve")
            .then().statusCode(status);
    }

    @Test void continuesBeyondTwoHundredCoolingDownTiles() {
        for (int i=0;i<201;i++) {
            Tile tile = new Tile(0,i*0.5-100);
            ViewportInterestEntity interest = new ViewportInterestEntity();
            interest.id=tile.key(); interest.tileLat=tile.lat(); interest.tileLon=tile.lon();
            interest.hitCount=201-i; interest.persist();
            if (i<200) {
                ImportedAreaEntity area=new ImportedAreaEntity();
                area.id=tile.key(); area.status="SUCCESS";
                area.lastAttemptAt=LocalDateTime.now().toString(); area.persist();
            }
        }
        assertEquals(new Tile(0,0),service.pickNextTile());
    }

    @Test void failedImportBacksOffAndRetriesAfterCooldown() {
        var server=ExternalApiWireMockResource.server();
        server.stubFor(post(urlEqualTo("/api/interpreter")).willReturn(serverError()));
        Tile tile=new Tile(58.5,17.5);
        ViewportInterestEntity interest=new ViewportInterestEntity();
        interest.id=tile.key(); interest.tileLat=tile.lat(); interest.tileLon=tile.lon();
        interest.hitCount=1; interest.persist();
        service.importTile(tile);
        ImportedAreaEntity area=ImportedAreaEntity.findById(tile.key());
        assertEquals("FAILED",area.status);
        assertNull(service.pickNextTile());
        area.lastAttemptAt=LocalDateTime.now().minusMinutes(31).toString(); area.update();
        assertEquals(tile,service.pickNextTile());
        server.stubFor(post(urlEqualTo("/api/interpreter")).willReturn(okJson("{\"elements\":[]}")));
        service.importTile(tile);
        area=ImportedAreaEntity.findById(tile.key());
        assertEquals("SUCCESS",area.status);
        assertNull(area.errorMessage);
        assertNull(service.pickNextTile());
    }
}
