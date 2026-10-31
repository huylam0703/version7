package vn.farm.farmdevice.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.farm.core.BusinessException;

class GeoRulesTest {
    private final ObjectMapper om = new ObjectMapper();

    private static final String POLYGON =
            "{\"type\":\"Polygon\",\"coordinates\":[[[106.0,10.0],[106.1,10.0],[106.1,10.1],[106.0,10.0]]]}";

    @Test
    void acceptsValidPolygonAndEmpty() {
        assertThatCode(() -> GeoRules.checkGeoJson(om, POLYGON)).doesNotThrowAnyException();
        assertThatCode(() -> GeoRules.checkGeoJson(om, null)).doesNotThrowAnyException();
        assertThatCode(() -> GeoRules.checkGeoJson(om, " ")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonJsonAndWrongType() {
        assertThatThrownBy(() -> GeoRules.checkGeoJson(om, "không phải json"))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", "INVALID_GEOJSON");
        assertThatThrownBy(() -> GeoRules.checkGeoJson(om, "{\"type\":\"Point\",\"coordinates\":[1,2]}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsOpenRing() {
        String open = "{\"type\":\"Polygon\",\"coordinates\":[[[106.0,10.0],[106.1,10.0],[106.1,10.1],[106.2,10.2]]]}";
        assertThatThrownBy(() -> GeoRules.checkGeoJson(om, open)).isInstanceOf(BusinessException.class);
    }

    @Test
    void pointMustComeInPairAndInRange() {
        assertThatCode(() -> GeoRules.checkPoint(10.5, 106.7)).doesNotThrowAnyException();
        assertThatCode(() -> GeoRules.checkPoint(null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> GeoRules.checkPoint(10.5, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GeoRules.checkPoint(91.0, 10.0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GeoRules.checkPoint(10.0, 181.0)).isInstanceOf(BusinessException.class);
    }
}
