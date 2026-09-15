package com.windsurf.service;

import com.windsurf.client.OverpassDiscoveryClient.*;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SpotDiscoveryServiceTest {
    private SpotDiscoveryService service() {
        var service = new SpotDiscoveryService();
        service.clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);
        return service;
    }

    @Test void mapsNodesWaysAndRelationsWithoutInventingRatings() {
        var s = service();
        s.client = query -> {
            assertTrue(query.contains("nwr["));
            assertTrue(query.contains("out center 100"));
            assertTrue(query.contains("39.0,2.0,40.0,3.0"));
            return new Result(List.of(
                    new Element("node", 1, 39.5, 2.5, null, Map.of("name", "Beach")),
                    new Element("way", 1, null, null, new Center(39.6, 2.6), Map.of()),
                    new Element("relation", 1, null, null, new Center(39.7, 2.7), Map.of()),
                    new Element("node", 2, null, null, null, Map.of()),
                    new Element("node", 3, 41.0, 2.5, null, Map.of())), null);
        };
        var result = s.discover(39, 2, 40, 3);
        assertEquals(List.of("osm-1", "osm-way-1", "osm-relation-1"), result.spots().stream().map(SpotDiscoveryService.Candidate::id).toList());
        assertTrue(result.spots().get(0).description().contains("https://www.openstreetmap.org/node/1"));
    }

    @Test void cachesNearbyBoxesAndExpiresAfterAnHour() {
        var s = service();
        var calls = new AtomicInteger();
        s.client = query -> { calls.incrementAndGet(); return new Result(List.of(), null); };
        s.discover(39.01, 2.01, 39.99, 2.99);
        s.discover(39.02, 2.02, 39.98, 2.98);
        assertEquals(1, calls.get());
        s.clock = Clock.offset(s.clock, Duration.ofHours(2));
        s.discover(39.01, 2.01, 39.99, 2.99);
        assertEquals(2, calls.get());
    }

    @Test void rejectsOversizedAndInvalidBoxesBeforeCallingOverpass() {
        var s = service();
        s.client = query -> { fail("Must not call Overpass"); return null; };
        assertThrows(BadRequestException.class, () -> s.discover(30, 2, 40, 3));
        assertThrows(BadRequestException.class, () -> s.discover(Double.NaN, 2, 40, 3));
        assertThrows(BadRequestException.class, () -> s.discover(39, 179, 40, -179));
        assertThrows(BadRequestException.class, () -> s.discover(40, 2, 39, 3));
    }

    @Test void failuresAndPartialResponsesBackOffThenRecover() {
        var s = service();
        var calls = new AtomicInteger();
        s.client = query -> { calls.incrementAndGet(); throw new IllegalStateException("unavailable"); };
        assertNotNull(s.discover(39, 2, 40, 3).message());
        assertNotNull(s.discover(39, 2, 40, 3).message());
        assertEquals(1, calls.get());
        s.clock = Clock.offset(s.clock, Duration.ofMinutes(2));
        s.client = query -> new Result(List.of(), "runtime error: timeout");
        assertNotNull(s.discover(39, 2, 40, 3).message());
        s.clock = Clock.offset(s.clock, Duration.ofMinutes(2));
        s.client = query -> new Result(List.of(), null);
        assertNull(s.discover(39, 2, 40, 3).message());
    }
}
