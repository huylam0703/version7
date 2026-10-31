package vn.farm.farmdevice.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.farm.core.BusinessException;

/** Quy tắc VAL-06 (toạ độ) và VAL-07 (GeoJSON) trong workbook API. */
public final class GeoRules {
    private GeoRules() {}

    /** lat và lng phải đi cùng nhau; lat -90..90, lng -180..180. */
    public static void checkPoint(Number lat, Number lng) {
        if ((lat == null) != (lng == null)) {
            throw BusinessException.invalid("VALIDATION_FAILED", "Vĩ độ và kinh độ phải được cung cấp cùng nhau.");
        }
        if (lat == null) return;
        if (!inRange(lat.doubleValue(), -90, 90) || !inRange(lng.doubleValue(), -180, 180)) {
            throw BusinessException.invalid("VALIDATION_FAILED", "Vĩ độ phải trong -90..90 và kinh độ trong -180..180.");
        }
    }

    /** Chấp nhận Polygon, MultiPolygon, hoặc Feature chứa một trong hai. Rỗng = bỏ qua. */
    public static void checkGeoJson(ObjectMapper om, String json) {
        if (json == null || json.isBlank()) return;
        JsonNode node;
        try {
            node = om.readTree(json);
        } catch (Exception e) {
            throw bad("Ranh giới không phải JSON hợp lệ.");
        }
        if (node == null || !node.isObject()) throw bad("Ranh giới phải là một đối tượng GeoJSON.");
        if ("Feature".equals(node.path("type").asText())) node = node.path("geometry");

        String type = node.path("type").asText();
        JsonNode coords = node.path("coordinates");
        if (!coords.isArray() || coords.isEmpty()) throw bad("GeoJSON thiếu 'coordinates'.");
        switch (type) {
            case "Polygon" -> checkPolygon(coords);
            case "MultiPolygon" -> coords.forEach(GeoRules::checkPolygon);
            default -> throw bad("Ranh giới phải là Polygon hoặc MultiPolygon.");
        }
    }

    private static void checkPolygon(JsonNode rings) {
        if (!rings.isArray() || rings.isEmpty()) throw bad("Polygon không có vòng nào.");
        for (JsonNode ring : rings) {
            if (!ring.isArray() || ring.size() < 4) throw bad("Mỗi vòng của Polygon cần ít nhất 4 điểm.");
            for (JsonNode pos : ring) {
                if (!pos.isArray() || pos.size() < 2 || !pos.get(0).isNumber() || !pos.get(1).isNumber()) {
                    throw bad("Toạ độ trong GeoJSON phải là [lng, lat].");
                }
                if (!inRange(pos.get(0).asDouble(), -180, 180) || !inRange(pos.get(1).asDouble(), -90, 90)) {
                    throw bad("Toạ độ trong GeoJSON nằm ngoài phạm vi hợp lệ.");
                }
            }
            if (!ring.get(0).equals(ring.get(ring.size() - 1))) throw bad("Vòng của Polygon phải khép kín.");
        }
    }

    private static boolean inRange(double v, double min, double max) {
        return !Double.isNaN(v) && v >= min && v <= max;
    }

    private static BusinessException bad(String msg) {
        return BusinessException.invalid("INVALID_GEOJSON", msg);
    }
}
