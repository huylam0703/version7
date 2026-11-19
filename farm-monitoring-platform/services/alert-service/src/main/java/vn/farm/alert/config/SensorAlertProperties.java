
package vn.farm.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "farm.alert.sensor")
public class SensorAlertProperties {

    private Map<String, Threshold> thresholds = new HashMap<>();

    private Map<String, String> typeMappings = new HashMap<>();

    public Map<String, Threshold> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<String, Threshold> thresholds) {
        this.thresholds = thresholds;
    }

    public Map<String, String> getTypeMappings() {
        return typeMappings;
    }

    public void setTypeMappings(Map<String, String> typeMappings) {
        this.typeMappings = typeMappings;
    }

    public static class Threshold {

        private Double min;
        private Double max;

        public Double getMin() {
            return min;
        }

        public void setMin(Double min) {
            this.min = min;
        }

        public Double getMax() {
            return max;
        }

        public void setMax(Double max) {
            this.max = max;
        }
    }
}
