#pragma once

#include <cmath>
#include <cstdio>
#include <limits>
#include <string>

namespace farm {

enum class NodeProfile { Node1 = 1, Node2 = 2 };

constexpr const char* nodeName(NodeProfile profile) {
    return profile == NodeProfile::Node1 ? "NODE_1" : "NODE_2";
}

struct Readings {
    double temperature = std::numeric_limits<double>::quiet_NaN();
    double humidity = std::numeric_limits<double>::quiet_NaN();
    double light = std::numeric_limits<double>::quiet_NaN();
    double waterLevelRaw = std::numeric_limits<double>::quiet_NaN();
    double soilMoistureRaw = std::numeric_limits<double>::quiet_NaN();
};

struct ReadingsResponse {
    int status;
    std::string body;
};

inline void appendValidReading(std::string& body, const char* key, double value,
                               double min, double max, bool integerOnly = false) {
    if (!std::isfinite(value) || value < min || value > max
        || (integerOnly && value != std::trunc(value))) {
        return;
    }
    char number[32];
    const int length = std::snprintf(number, sizeof(number), integerOnly ? "%.0f" : "%.9g", value);
    if (length <= 0 || static_cast<std::size_t>(length) >= sizeof(number)) {
        return;
    }
    body += ",\"";
    body += key;
    body += "\":";
    body.append(number, static_cast<std::size_t>(length));
}

inline ReadingsResponse makeReadingsResponse(NodeProfile profile, const Readings& readings) {
    std::string body = std::string("{\"node\":\"") + nodeName(profile) + "\"";
    const auto nodeOnlySize = body.size();

    if (profile == NodeProfile::Node1) {
        constexpr int maxAdcCount = (1 << 12) - 1;
        appendValidReading(body, "waterLevelRaw", readings.waterLevelRaw, 0, maxAdcCount, true);
        appendValidReading(body, "soilMoistureRaw", readings.soilMoistureRaw, 0, maxAdcCount, true);
    } else {
        appendValidReading(body, "temperature", readings.temperature, -40, 85);
        appendValidReading(body, "humidity", readings.humidity, 0, 100);
        appendValidReading(body, "light", readings.light, 0, 100000);
    }

    if (body.size() == nodeOnlySize) {
        body += ",\"error\":\"NO_VALID_READINGS\"}";
        return {503, body};
    }
    body += '}';
    return {200, body};
}

} // namespace farm
