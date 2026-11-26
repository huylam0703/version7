#include "../ReadingsHttp.h"

#include <cassert>
#include <iostream>
#include <string>

using farm::NodeProfile;
using farm::Readings;
using farm::ReadingsResponse;
using farm::makeReadingsResponse;

ReadingsResponse node1Fixture() {
    Readings inputs;
    inputs.waterLevelRaw = 0;
    inputs.soilMoistureRaw = 2425;
    inputs.temperature = 33.2;
    return makeReadingsResponse(NodeProfile::Node1, inputs);
}

ReadingsResponse node2Fixture() {
    Readings inputs;
    inputs.temperature = 33.2;
    inputs.humidity = 65.9;
    inputs.light = 120.5;
    inputs.waterLevelRaw = 20;
    return makeReadingsResponse(NodeProfile::Node2, inputs);
}

ReadingsResponse partialFixture() {
    Readings inputs;
    inputs.temperature = std::numeric_limits<double>::infinity();
    inputs.humidity = 100.1;
    inputs.light = 0;
    return makeReadingsResponse(NodeProfile::Node2, inputs);
}

ReadingsResponse invalidFixture() {
    Readings inputs;
    inputs.waterLevelRaw = 4096;
    inputs.soilMoistureRaw = 1.5;
    return makeReadingsResponse(NodeProfile::Node1, inputs);
}

int main(int argc, char** argv) {
    const auto node1 = node1Fixture();
    const auto node2 = node2Fixture();
    const auto partial = partialFixture();
    const auto invalid = invalidFixture();

    if (argc == 2) {
        const std::string fixture = argv[1];
        const ReadingsResponse* selected = nullptr;
        if (fixture == "node1") selected = &node1;
        if (fixture == "node2") selected = &node2;
        if (fixture == "partial") selected = &partial;
        if (fixture == "invalid") selected = &invalid;
        if (!selected) return 2;
        std::cout << selected->status << '\n' << selected->body << '\n';
        return 0;
    }

    assert(node1.status == 200);
    assert(node1.body == R"({"node":"NODE_1","waterLevelRaw":0,"soilMoistureRaw":2425})");
    assert(node2.status == 200);
    assert(node2.body == R"({"node":"NODE_2","temperature":33.2,"humidity":65.9,"light":120.5})");
    assert(partial.status == 200);
    assert(partial.body == R"({"node":"NODE_2","light":0})");
    assert(invalid.status == 503);
    assert(invalid.body == R"({"node":"NODE_1","error":"NO_VALID_READINGS"})");

    const auto empty = makeReadingsResponse(NodeProfile::Node2, {});
    assert(empty.status == 503);
    assert(empty.body == R"({"node":"NODE_2","error":"NO_VALID_READINGS"})");

    Readings boundary;
    boundary.waterLevelRaw = -1;
    boundary.soilMoistureRaw = 4095;
    const auto adc = makeReadingsResponse(NodeProfile::Node1, boundary);
    assert(adc.status == 200);
    assert(adc.body == R"({"node":"NODE_1","soilMoistureRaw":4095})");
    std::cout << "PASS: 6 readings response scenarios\n";
}
