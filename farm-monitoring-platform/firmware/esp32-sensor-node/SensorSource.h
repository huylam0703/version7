#pragma once

#include <Arduino.h>

#if SENSOR_NODE_NUMBER == 2
#include <BH1750.h>
#include <DHT.h>
#include <Wire.h>
#endif

#include "ReadingsHttp.h"

namespace farm {
namespace sensors {

#if SENSOR_NODE_NUMBER == 1

constexpr int waterLevelPin = 35;
constexpr int soilMoisturePin = 34;

inline void begin() {
    // GPIO 34 and 35 are ADC1 inputs on ESP32-WROOM-32.
    pinMode(waterLevelPin, INPUT);
    pinMode(soilMoisturePin, INPUT);
    analogReadResolution(12);
}

inline Readings sample() {
    Readings readings;
    readings.waterLevelRaw = analogRead(waterLevelPin);
    readings.soilMoistureRaw = analogRead(soilMoisturePin);
    return readings;
}

#else

constexpr int dhtPin = 4;
constexpr int lightSda = 21;
constexpr int lightScl = 22;
constexpr uint8_t lightAddress = 0x23;

static DHT dht(dhtPin, DHT22);
static BH1750 lightMeter(lightAddress);
static bool lightInitialized = false;

inline void begin() {
    dht.begin();
    if (Wire.begin(lightSda, lightScl)) {
        lightInitialized = lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE);
    }
    if (!lightInitialized) {
        Serial.println("[NODE_2] BH1750 initialization failed");
    }
}

inline Readings sample() {
    Readings readings;
    readings.temperature = dht.readTemperature();
    readings.humidity = dht.readHumidity();
    if (lightInitialized && lightMeter.measurementReady()) {
        readings.light = lightMeter.readLightLevel();
    }
    return readings;
}

#endif

} // namespace sensors
} // namespace farm
