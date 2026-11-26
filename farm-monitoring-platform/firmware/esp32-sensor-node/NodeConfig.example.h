#pragma once

// Copy to NodeConfig.local.h and set the Wi-Fi credentials locally.
// Node 1 is Water/Soil and Node 2 is DHT22/BH1750. PlatformIO selects it by environment.
#ifndef SENSOR_NODE_NUMBER
#define SENSOR_NODE_NUMBER 1
#endif
#define SENSOR_NODE_WIFI_SSID ""
#define SENSOR_NODE_WIFI_PASSWORD ""
