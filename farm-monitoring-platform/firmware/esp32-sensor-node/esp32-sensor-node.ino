#include <Arduino.h>
#include <WebServer.h>
#include <WiFi.h>

#include "ReadingsHttp.h"

#if __has_include("NodeConfig.local.h")
#include "NodeConfig.local.h"
#endif

#ifndef SENSOR_NODE_NUMBER
#define SENSOR_NODE_NUMBER 1
#endif
#ifndef SENSOR_NODE_WIFI_SSID
#define SENSOR_NODE_WIFI_SSID ""
#endif
#ifndef SENSOR_NODE_WIFI_PASSWORD
#define SENSOR_NODE_WIFI_PASSWORD ""
#endif

#include "SensorSource.h"

static_assert(SENSOR_NODE_NUMBER == 1 || SENSOR_NODE_NUMBER == 2,
              "SENSOR_NODE_NUMBER must be 1 or 2");

namespace {

constexpr auto profile = static_cast<farm::NodeProfile>(SENSOR_NODE_NUMBER);
constexpr const char* nodeName = farm::nodeName(profile);
constexpr char wifiSsid[] = SENSOR_NODE_WIFI_SSID;
constexpr char wifiPassword[] = SENSOR_NODE_WIFI_PASSWORD;
constexpr unsigned long reconnectIntervalMs = 15000;

WebServer server(80);
bool servingHttp = false;
IPAddress servedIp;
unsigned long lastConnectionAttempt = 0;

void sendReadings() {
    const auto response = farm::makeReadingsResponse(profile, farm::sensors::sample());
    server.sendHeader("Cache-Control", "no-store");
    server.send(response.status, "application/json", response.body.c_str());
}

void handleUnknownRequest() {
    if (server.uri() == "/readings") {
        server.sendHeader("Allow", "GET");
        server.send(405, "application/json", "{\"error\":\"METHOD_NOT_ALLOWED\"}");
        return;
    }
    server.send(404, "application/json", "{\"error\":\"NOT_FOUND\"}");
}

void serviceWifi() {
    const IPAddress ip = WiFi.localIP();
    if (WiFi.status() == WL_CONNECTED && ip != IPAddress(0, 0, 0, 0)) {
        if (!servingHttp || servedIp != ip) {
            if (servingHttp) {
                server.stop();
            }
            server.begin();
            servingHttp = true;
            servedIp = ip;
            Serial.printf("[%s] IP: %s | GET http://%s/readings\n",
                          nodeName, ip.toString().c_str(), ip.toString().c_str());
        }
        server.handleClient();
        return;
    }

    if (servingHttp) {
        server.stop();
        servingHttp = false;
        Serial.printf("[%s] Wi-Fi disconnected\n", nodeName);
    }

    const unsigned long now = millis();
    if (now - lastConnectionAttempt >= reconnectIntervalMs) {
        lastConnectionAttempt = now;
        Serial.printf("[%s] Retrying Wi-Fi\n", nodeName);
        WiFi.reconnect();
    }
}

} // namespace

void setup() {
    Serial.begin(115200);
    if (wifiSsid[0] == '\0') {
        Serial.printf("[%s] Set SENSOR_NODE_WIFI_SSID in NodeConfig.local.h\n", nodeName);
        return;
    }

    farm::sensors::begin();
    server.on("/readings", HTTP_GET, sendReadings);
    server.onNotFound(handleUnknownRequest);
    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(false);  // serviceWifi owns the retry schedule.
    lastConnectionAttempt = millis();
    Serial.printf("[%s] Connecting to Wi-Fi\n", nodeName);
    WiFi.begin(wifiSsid, wifiPassword[0] == '\0' ? nullptr : wifiPassword);
}

void loop() {
    if (wifiSsid[0] != '\0') {
        serviceWifi();
    }
    delay(2);
}
