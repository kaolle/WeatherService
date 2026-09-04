package com.windsurf.client;

import java.util.List;
import java.util.Map;

public record OverpassResponse(List<Element> elements) {

    public record Element(String type, long id, double lat, double lon, Map<String, String> tags) {
        public Element {
            if (tags == null) tags = Map.of();
        }
    }
}
