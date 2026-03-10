/**
 * Driver:     Ecowitt WiFi Gateway
 * Author:     Simon Burke (Original author Mirco Caramori - github.com/mircolino)
 * Repository: https://github.com/sburke781/ecowitt
 * Import URL: https://raw.githubusercontent.com/sburke781/ecowitt/main/ecowitt_gateway.groovy
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Change Log:
 *
 * 2020.04.24 - Initial implementation
 * 2020.04.29 - Added GitHub versioning
 *            - Added support for more sensors: WH40, WH41, WH43, WS68 and WS80
 * 2020.04.29 - Added sensor battery range conversion to 0-100%
 * 2020.05.03 - Optimized state dispatch and removed unnecessary attributes
 * 2020.05.04 - Added metric/imperial unit conversion
 * 2020.05.05 - Gave child sensors a friendlier default name
 * 2020.05.08 - Further state optimization and release to stable
 * 2020.05.11 - HTML templates
 *            - Normalization of floating values
 * 2020.05.12 - Added windDirectionCompass, ultravioletDanger, ultravioletColor, aqiIndex, aqiDanger, aqiColor attributes
 * 2020.05.13 - Improved PM2.5 -> AQI range conversion
 *            - HTML template syntax checking and optimization
 *            - UI error handling using red-colored state text messages
 * 2020.05.14 - Major refactoring and architectural change
 *            - PWS like the WS2902 are recognized and no longer split into multiple child sensors
 *            - Rain (WH40), Wind and Solar (WH80) and Outdoor Temp/Hum (WH32) if detected, are combined into a single
 *              virtual WS2902 PWS to improve HTML Templating
 *            - Fixed several imperial-metric conversion issues
 *            - Metric pressure is now converted to hPa
 *            - Laid the groundwork for identification and support of sensors WH41, WH55 and WH57
 *            - Added several calculated values such as windChill, dewPoint, heatIndex etc. with color and danger levels
 *            - Time of data received converted from UTC to hubitat default locale format
 *            - Added error handling using state variables
 *            - Code optimization
 * 2020.05.22 - Added orphaned sensor garbage collection using "Resync Sensors" commands
 * 2020.05.23 - Fixed a bug in the PM2.5 to AQI conversion
 * 2020.05.24 - Fixed a possible command() and parse() race condition
 * 2020.05.26 - Added icons support in the HTML template
 * 2020.05.30 - Added HTML template repository
 *            - Added support for multiple (up to 5) HTML template to each child sensor
 *            - Fixed wind icon as direction is reported as "from" where the wind originates
 * 2020.06.01 - Fixed a cosmetic bug where "pending" status would not be set on non-existing attributes
 * 2020.06.02 - Added visual confirmation of "resync sensors pending"
 * 2020.06.03 - Added last data received timestamp to the child drivers to easily spot if data is not being received from the sensor
 *            - Added battery icons (0%, 20%, 40%, 60%, 80%, 100%)
 *            - Reorganized error e/o status reporting, now displayed in a dedicated "status" attribute
 * 2020.06.04 - Added the ability to enter the MAC address directly as a DNI in the parent device creation page
 * 2020.06.05 - Added support for both MAC and IP addresses (since MACs don't work across VLANs)
 * 2020.06.06 - Add importURL for easier updating
 * 2020.06.08 - Added support for Lightning Detection Sensor (WH57)
 * 2020.06.08 - Added support for Multi-channel Water Leak Sensor (WH55)
 * 2020.06.21 - Added support for pressure correction to sea level based on altitude and temperature
 * 2020.06.22 - Added preference to let the end-user decide whether to compound or not outdoor sensors
 *              Added custom battery attributes in bundled PWS sensors
 * 2020.08.27 - Added user defined min/max voltage values to fine-tune battery status in sensors reporting it as voltage range
 *              Added Hubitat Package Manager repository tags
 * 2020.08.27 - Fixed null exception caused by preferences being set asynchronously
 *            - Removed sensor "time" attribute which could cause excessive sendEvent activity
 * 2020.08.31 - Added support for new Indoor Air Quality Sensor (WH45)
 *            - Optimized calculation of inferred values: dewPoint, heatIndex, windChill and AQI
 * 2020.09.08 - Added support for Water/Soil Temperature Sensor (WH34)
 * 2020.09.17 - Added (back) real-time AQI index, color and danger
 * 2020.09.20 - https://github.com/lymanepp: Added Summer Simmer Index attributes
 *            - Added preferences to selectively calculate HeatIndex, SimmerIndex, WindChill and DewPoint on a per-sensor basis
 * 2020.09.21 - https://github.com/lymanepp: Improved accuracy of dew point calculations
 * 2020.10.02 - Added WeatherFlow Smart Weather Stations local UDP support
 * 2020.10.06 - Fixed a minor issue with lightning attributes
 *            - Added new templates to the template repository
 * 2020.10.09 - Fixed a regression causing a null exception when the lightning sensor reports no strikes
 * 2020.10.27 - Changed the sensor DNI naming scheme which prevented the support for multiple gateways
 * 2020.10.29 - In a virtual (bundled) PWS, now each individual component is correctly identified if orphaned
 *            - Added safeguards for heat, summer simmer and wind chill indexes to prevent invalid values when temperature is
 *              above or below a certain threshold
 * 2021.02.04 - Added support for humidityAbs (absolute humidity) based on current relative humidity and temperature
 * 2021.02.06 - Fixed WH45 temperature and humidity signature
 * 2021.02.08 - Added "Carbon Dioxide Measurement" capability
 *            - Renamed attributes "co2" to native "carbonDioxide" and "co2_avg_24h" to "carbonDioxide_avg_24h"
 *            - When a sensor is on USB power, battery attributes are no longer created
 * 2021.05.18 - streamlined double conversion in attributeUpdateDewPoint()
 * 2021.06.02 - bug fixing
 * 2021.08.11 - updated status attribute to be deleted when no error
 *            - added the ability to set the number of digits for temperature and pressure
 *            - added the ability to completely disable html template support including all related attributes
 *            - fixed a bug where the soil moisture sensor would incorreclty display Dew Point and Heat Index preferences
 *            - used the new (2.2.8) API deleteCurrentState() to remove stale attributes when toggling Dew Point, Heat Index
 *              and Wind Chill support
 *            - improved and optimized device orphaned status detection
 * 2021.08.18 - relocated repository: mircolino -> padus
 * 2021.08.25 - relocated repository: padus -> sburke781
 *            - moved to ecowitt namespace
 * 2021.12.04 - Replaced "time" attribute with lastUpdate, thanks to @kahn-hubitat for writing and testing this change
 * 2021.12.04 - Added nameserver lookup for remote gateways where their public IP address can change, thanks again to @kahn-hubitat
 * 2022.02.02 - Added Air Quality capability and population of associated Air Quality attribute in sensor driver, thanks @kahn-hubitat
 * 2022.02.03 - Fixed bug with Air Quality update where it would only happen when HTML tile was enabled
 * 2022.06.17 - Added support for Leaf Wetness Sensor
 * 2022.06.17 - Leaf Sensor adjustments for version handling
 * 2022.07.04 - Fix for WH31 Battery Readings not being picked up correctly
 * 2022.07.09 - Formatting of Dynamic DNS Preference title and description
 * 2022.10.22 - Add Wittboy Weather Station support (WH/WS90) - developed by @kahn-hubitat
 * 2022.12.15 - Added Wittboy (WS90) rain readings to child sensor driver
 * 2023.01.01 - Added wh90batt to sensor detection for detecting WittBoy PWS
 * 2023-02-05 - Added ws90cap_volt reading (Wittboy Battery)
 * 2023-02-18 - Added version check to parse method
 * 2023-07-02 - Fix for Dew Point in Celsius
 * 2023-09-24 - Updates for Wittboy battery readings and firmware (made by @xcguy)
 * 2023-09-24 - New runtime attribute, dateutc stored in data value and detection of gain30_piezo (not stored)
 * 2023-09-25 - Fixed error in Lightning Distance reporting in KMs instead of miles
 * 2023-10-22 - Added option to forward data feed on to another hub
 * 2023-12-03 - Added Git Repo Version Monitoring setting and logic
 * 2024-12-xx - lgk - add srain_piezo = 0 1 and associated raining = true false, also firmware version/ws90_ver and ws90cap_volt firmware version and capacitor voltage are stuckon the wind device for now
 * 2025-01-14 - lgk - fixed missing break statement when processing srain_piezo and raining attributes
 * 2025-07-03 - ikishk and lgk - cater for 16 soil moisture channels, addition of soilAD reading for soil moisture sensors (seems to be raw milivolt reading)
 * 2025-07-04 - lgk - added updateInterval and vpd attributes
 * 2026.01.27 - Manus - Added closure-based logging and rain sensor selection logic
 */
import groovy.json.JsonSlurper;

public static String version() { return "v1.35.00"; }
public static String gitHubUser() { return "sburke781"; }
public static String gitHubRepo() { return "ecowitt"; }
public static String gitHubBranch() { return "main"; }

import groovy.transform.Field

// In-memory attribute cache to reduce device.currentValue() database reads
@Field static final java.util.concurrent.ConcurrentHashMap attributeCache = new java.util.concurrent.ConcurrentHashMap()

// Cached logging level to avoid conversion on every log call (-1 = not yet cached)
@Field static int cachedLoggingLevel = -1

// Cached enabled sensor IDs set, rebuilt only on updated()
@Field static volatile Set cachedEnabledSensorIds = null

// Pre-compiled regex patterns for sensor key matching
@Field static final java.util.regex.Pattern RE_BATT_1_8 = ~/batt([1-8])/
@Field static final java.util.regex.Pattern RE_TEMP_1_8 = ~/temp([1-8])f/
@Field static final java.util.regex.Pattern RE_HUMIDITY_1_8 = ~/humidity([1-8])/
@Field static final java.util.regex.Pattern RE_SOILBATT = ~/soilbatt([1-9]|1[0-6])$/
@Field static final java.util.regex.Pattern RE_SOILMOISTURE = ~/soilmoisture([1-9]|1[0-6])$/
@Field static final java.util.regex.Pattern RE_SOILAD = ~/soilad([1-9]|1[0-6])$/
@Field static final java.util.regex.Pattern RE_PM25BATT = ~/pm25batt([1-4])/
@Field static final java.util.regex.Pattern RE_PM25_CH = ~/pm25_ch([1-4])/
@Field static final java.util.regex.Pattern RE_PM25_AVG = ~/pm25_avg_24h_ch([1-4])/
@Field static final java.util.regex.Pattern RE_LEAKBATT = ~/leakbatt([1-4])/
@Field static final java.util.regex.Pattern RE_LEAK_CH = ~/leak_ch([1-4])/
@Field static final java.util.regex.Pattern RE_TF_BATT = ~/tf_batt([1-8])/
@Field static final java.util.regex.Pattern RE_TF_CH = ~/tf_ch([1-8])/
@Field static final java.util.regex.Pattern RE_LEAF_BATT = ~/leaf_batt([1-8])/
@Field static final java.util.regex.Pattern RE_LEAFWETNESS = ~/leafwetness_ch([1-8])/
@Field static final java.util.regex.Pattern RE_BATT_WF = ~/batt_wf([1-8])/
@Field static final java.util.regex.Pattern RE_TEMPF_WF = ~/tempf_wf([1-8])/
@Field static final java.util.regex.Pattern RE_HUMIDITY_WF = ~/humidity_wf([1-8])/
@Field static final java.util.regex.Pattern RE_BAROMRELIN_WF = ~/baromrelin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_BAROMABSIN_WF = ~/baromabsin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_LIGHTNING_WF = ~/lightning_wf([1-8])/
@Field static final java.util.regex.Pattern RE_LIGHTNING_TIME_WF = ~/lightning_time_wf([1-8])/
@Field static final java.util.regex.Pattern RE_LIGHTNING_ENERGY_WF = ~/lightning_energy_wf([1-8])/
@Field static final java.util.regex.Pattern RE_LIGHTNING_NUM_WF = ~/lightning_num_wf([1-8])/
@Field static final java.util.regex.Pattern RE_UV_WF = ~/uv_wf([1-8])/
@Field static final java.util.regex.Pattern RE_SOLAR_WF = ~/solarradiation_wf([1-8])/
@Field static final java.util.regex.Pattern RE_RAINRATE_WF = ~/rainratein_wf([1-8])/
@Field static final java.util.regex.Pattern RE_EVENTRAIN_WF = ~/eventrainin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_HOURLYRAIN_WF = ~/hourlyrainin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_DAILYRAIN_WF = ~/dailyrainin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_WEEKLYRAIN_WF = ~/weeklyrainin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_MONTHLYRAIN_WF = ~/monthlyrainin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_YEARLYRAIN_WF = ~/yearlyrainin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_TOTALRAIN_WF = ~/totalrainin_wf([1-8])/
@Field static final java.util.regex.Pattern RE_WINDDIR_WF = ~/winddir_wf([1-8])/
@Field static final java.util.regex.Pattern RE_WINDDIR_AVG_WF = ~/winddir_avg10m_wf([1-8])/
@Field static final java.util.regex.Pattern RE_WINDSPEED_WF = ~/windspeedmph_wf([1-8])/
@Field static final java.util.regex.Pattern RE_WINDSPD_AVG_WF = ~/windspdmph_avg10m_wf([1-8])/
@Field static final java.util.regex.Pattern RE_WINDGUST_WF = ~/windgustmph_wf([1-8])/
@Field static final java.util.regex.Pattern RE_MAXGUST_WF = ~/maxdailygust_wf([1-8])/

// O(1) lookup map: string key → sensor ID (replaces sequential switch/case for known keys)
@Field static final Map SENSOR_KEY_MAP = [
    // Integrated/Indoor Ambient Sensor (WH25) -> sensor id: 1
    "wh25batt": 1, "tempinf": 1, "humidityin": 1, "baromrelin": 1, "baromabsin": 1,
    // Outdoor Ambient Sensor (WH26 -> WH80 -> WH69) -> sensor id: 2
    "wh26batt": 2, "tempf": 2, "humidity": 2, "vpd": 2,
    // Rain Gauge Sensor (WH40 -> WH69) -> sensor id: 4
    "wh40batt": 4, "rainratein": 4, "eventrainin": 4, "hourlyrainin": 4,
    "dailyrainin": 4, "weeklyrainin": 4, "monthlyrainin": 4, "yearlyrainin": 4, "totalrainin": 4,
    // Rain (WS90) -> sensor id: 13
    "rrain_piezo": 13, "erain_piezo": 13, "hrain_piezo": 13, "drain_piezo": 13,
    "wrain_piezo": 13, "mrain_piezo": 13, "yrain_piezo": 13, "train_piezo": 13, "srain_piezo": 13,
    // Air Quality Monitor (WH45) -> sensor id: 5
    "tf_co2": 5, "humi_co2": 5, "pm25_co2": 5, "pm25_24h_co2": 5,
    "pm10_co2": 5, "pm10_24h_co2": 5, "co2": 5, "co2_24h": 5, "co2_batt": 5,
    // Lightning Detection Sensor (WH57) -> sensor id: 8
    "wh57batt": 8, "lightning": 8, "lightning_num": 8, "lightning_time": 8,
    // Wind & Solar Sensor (WH80 -> WH69, WS90) -> sensor id: 9
    "wh65batt": 9, "wh68batt": 9, "wh80batt": 9, "wh90batt": 9,
    "ws80cap_volt": 9, "ws90cap_volt": 9, "ws80_ver": 9, "ws90_ver": 9,
    "winddir": 9, "winddir_avg10m": 9, "windspeedmph": 9, "windspdmph_avg10m": 9,
    "windgustmph": 9, "maxdailygust": 9, "uv": 9, "solarradiation": 9,
]

// Metadata -------------------------------------------------------------------------------------------------------------------

metadata {
  
  definition(name: "Ecowitt WiFi Gateway", namespace: "ecowitt", author: "Simon Burke", importUrl: "https://raw.githubusercontent.com/${gitHubUser()}/${gitHubRepo()}/${gitHubBranch()}/ecowitt_gateway.groovy") {
    capability "Sensor";

    command "resyncSensors";

    // Gateway info
    attribute "driver", "string";                              // Driver version (new version notification)
    attribute "mac", "string";                                 // Address (either MAC or IP)
    attribute "model", "string";                               // Model number
    attribute "firmware", "string";                            // Firmware version
    attribute "rf", "string";                                  // Sensors radio frequency
    attribute "passkey", "string";                             // PASSKEY

    attribute "lastUpdate", "string";                          // Time last data was posted
    attribute "status", "string";                              // Display current driver status
    attribute "dynamicIPResult","string"                       // Result of nameserver lookup
    attribute "runtime","number"                               // Run time
    attribute "updateInterval","number"                        // Time between custom data feeds being sent from the EcoWitt Gateway to HE
  }

  preferences {
    input(name: "macAddress", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>MAC / IP Address</font>", description: "<font style='font-size:12px; font-style: italic'>Wi-Fi gateway MAC or IP address</font>", defaultValue: "", required: true);
    input(name: "DDNSName", type: "text", title: "<font style='font-size:12px; color:#1a77c9'>DDNS Name</font>", description: "<font style='font-size:12px; font-style: italic'>Dynamic DNS Name to use to resolve a changing ip address. Leave Blank if not used.</font>", required: false)
    input(name: "DDNSRefreshTime", type: "number", title: "<font style='font-size:12px; color:#1a77c9'>DDNS Refresh Time (Hours)</font>",description: "<font style='font-size:12px; font-style: italic'>How often (in Hours) to check/resolve the DDNS Name to discover an IP address change on a remote weather station? (Range 1 - 720, Default 24)?</font>", range: "1..720", defaultValue: 3, required: false)
    input(name: "forwardAddress", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Forwarding IP Address</font>", description: "<font style='font-size:12px; font-style: italic'>IP address of hub to forward data feed to (optional)</font>", defaultValue: "", required: false);
    input(name: "forwardPort", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Forwarding Port</font>", description: "<font style='font-size:12px; font-style: italic'>Port of hub to forward data feed to (optional)</font>", defaultValue: "", required: false);
    input(name: "forwardPath", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Forwarding Path</font>", description: "<font style='font-size:12px; font-style: italic'>Path of hub to forward data feed to (optional)</font>", defaultValue: "", required: false);
    input(name: "bundleSensors", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Compound Outdoor Sensors</font>", description: "<font style='font-size:12px; font-style: italic'>Combine sensors in a virtual PWS array</font>", defaultValue: true);
    input(name: "unitSystem", type: "enum", title: "<font style='font-size:12px; color:#1a77c9'>System of Measurement</font>", description: "<font style='font-size:12px; font-style: italic'>Unit system all values are converted to</font>", options: [0:"Imperial", 1:"Metric"], multiple: false, defaultValue: 0, required: true);
    input(name: "monitorGitVersion", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Monitor Git Driver Version</font>", description: "<font style='font-size:12px; font-style: italic'>Check Git Repository for New Driver Version</font>", defaultValue: true);
    input(name: "enabledSensors", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Enabled Sensor Types</font>", description: "<font style='font-size:12px; font-style: italic'>Comma-separated list of sensor types you own. Only data from listed sensors will be processed. Gateway attributes (model, firmware, etc.) are always handled.<br/>Valid values: WH25 (Indoor), WH26 (Outdoor Temp/Hum), WH31 (Multi-ch Ambient), WH34 (Soil/Water Temp), WH40 (Rain Gauge), WH45 (Air Quality, also WH41), WH51 (Soil Moisture), WH55 (Water Leak), WH57 (Lightning), WH80 or WS90 (Wind/Solar), WS90_RAIN (WS90 Piezo Rain), WN35 (Leaf Wetness), WFST (WeatherFlow)</font>", defaultValue: "WH25,WH26,WH31,WH34,WH40,WH45,WH51,WH55,WH57,WH80,WS90,WS90_RAIN,WN35,WFST");
    input(name: "reportTimers", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Report Last Update Time</font>", description: "<font style='font-size:12px; font-style: italic'>Enable uptime, lastUpdate and dateUTC updates on every data reception</font>", defaultValue: false);
    input(name: 'loggingLevel', type: 'enum', title: 'Logging level', options: ['1':'Error', '2':'Warning', '3':'Info', '4':'Debug', '5':'Trace'], defaultValue: '3', required: true)
  }
}

/*
 * Data variables used by the driver:
 *
 * "sensorResync"                                              // User command triggered condition to cleanup/resynchronize the sensors
 * "sensorMap"                                                 // Map of whether sensors have been combined or not into a PWS
 * "sensorBundled"                                             // "true" is we have an actual bundled PWS
 * "sensorList"                                                // List of children IDs
 */

// Preferences ----------------------------------------------------------------------------------------------------------------

private String gatewayMacAddress() {
  //
  // Return the MAC or IP address as entered by the user, or the current DNI if one hasn't been entered yet
  //
  String address = settings.macAddress as String;

  if (address == null) {
    //
    // *** This is a timing hack ***
    // When the users sets the DNI at installation, we update the settings before
    // calling update() but when we get here the setting is still null!
    //
    address = device.getDeviceNetworkId();
  }

  return (address);
}

// ------------------------------------------------------------

private Boolean bundleOutdoorSensors() {
  //
  // Return true if outdoor sensors are to be bundled together
  //
  if (settings.bundleSensors != null) return (settings.bundleSensors);
  return (true);
}

Boolean unitSystemIsMetric() {
  //
  // Return true if the selected unit system is metric
  // Declared public because it's being used by the child-devices
  //
  if (settings.unitSystem != null) return (settings.unitSystem.toInteger() != 0);
  return (false);
}

// ------------------------------------------------------------

private Boolean monitorGitVersion() {
  //
  // Return true if we are monitoring the Git repository for updates
  //
  if (settings.monitorGitVersion != null) return (settings.monitorGitVersion);
  return (true);
}

// Logging --------------------------------------------------------------------------------------------------------------------

private void logger(level, message) {
    int configuredLevel = getCachedLoggingLevel()
    switch (level) {
        case 'E': if (configuredLevel >= 1) { log.error(getLogMessage(message)) }; break
        case 'W': if (configuredLevel >= 2) { log.warn(getLogMessage(message))  }; break
        case 'I': if (configuredLevel >= 3) { log.info(getLogMessage(message))  }; break
        case 'D': if (configuredLevel >= 4) { log.debug(getLogMessage(message)) }; break
        case 'T': if (configuredLevel >= 5) { log.trace(getLogMessage(message)) }; break
    }
}

private String getLogMessage(message) {
  def text = (message instanceof Closure) ? message() : message
  return "${device.displayName}: ${text}"
}

private void updateCachedLoggingLevel() {
  cachedLoggingLevel = (settings.loggingLevel as String).toInteger()
}

private int getCachedLoggingLevel() {
  if (cachedLoggingLevel >= 0) return cachedLoggingLevel
  cachedLoggingLevel = (settings.loggingLevel as String)?.toInteger() ?: 3
  return cachedLoggingLevel
}

// Attribute cache helpers (reduce device.currentValue() database reads) -------------------------------------------------------

private Map<String, Object> getAttrCache() {
  return attributeCache.computeIfAbsent(device.getId()) { new java.util.concurrent.ConcurrentHashMap<String, Object>() }
}

private void invalidateCache() {
  attributeCache.remove(device.getId())
}

private String cachedString(String attribute) {
  Map cache = getAttrCache()
  if (cache.containsKey(attribute)) return cache[attribute]
  return device.currentValue(attribute) as String
}

private Number cachedNumber(String attribute) {
  Map cache = getAttrCache()
  if (cache.containsKey(attribute)) return cache[attribute]
  return device.currentValue(attribute) as Number
}

// Versioning -----------------------------------------------------------------------------------------------------------------

private Map versionExtract(String ver) {
  //
  // Given any version string (e.g. version 2.5.78-prerelease) will return a Map as following:
  //   Map.major version
  //   Map.minor version
  //   Map.build version
  //   Map.desc  version
  // or "null" if no version info was found in the given string
  //
  Map val = null;

  if (ver) {
    String pattern = /.*?(\d+)\.(\d+)\.(\d+).*/;
    java.util.regex.Matcher matcher = ver =~ pattern;

    if (matcher.groupCount() == 3) {
      val = [:];
      val.major = matcher[0][1].toInteger();
      val.minor = matcher[0][2].toInteger();
      val.build = matcher[0][3].toInteger();
      val.desc = "v${val.major}.${val.minor}.${val.build}";
    }
  }

  return (val);
}

// ------------------------------------------------------------

Boolean versionUpdate() {
  //
  // Return true is a new version is available
  //
  logger('D', "versionUpdate()");

  Boolean ok = false;
  Boolean devOk = false;
  String attribute = "driver";

  // Retrieve current version from the driver
  Map verCur = versionExtract(version());
  // Retrieve the current version captured on the device
  String devVer = cachedString(attribute);

  // If the driver state variable has not been recorded on the device, update it
  if (devVer == null || devVer == "") {
    logger('D', "versionUpdate: device driver version was empty, populating it now");
    devOk = attributeUpdateString(verCur.desc, attribute);
    devVer = verCur.desc;
  }  

  // If we are monitoring Git for new driver version, check the manifest file and compare to the current driver version
  if(monitorGitVersion()) {

    try {
      
      if (verCur) {
        // Retrieve latest version from GitHub repository manifest
        // If the file is not found, it will throw an exception
        Map verNew = null;
        String manifestText = "https://raw.githubusercontent.com/${gitHubUser()}/${gitHubRepo()}/${gitHubBranch()}/packageManifest.json".toURL().getText();
        if (manifestText) {
          // text -> json
          Object parser = new groovy.json.JsonSlurper();
          Object manifest = parser.parseText(manifestText);

          verNew = versionExtract(manifest.version);
          if (verNew) {
            // Compare versions
            if (verCur.major > verNew.major) verNew = null;
            else if (verCur.major == verNew.major) {
              if (verCur.minor > verNew.minor) verNew = null;
              else if (verCur.minor == verNew.minor) {
                if (verCur.build >= verNew.build) verNew = null;
              }
            }
          }
        }

        String version = verCur.desc;
        if (verNew) version = "<font style='color:#3ea72d'>${verCur.desc} (${verNew.desc} available)</font>";
        ok = attributeUpdateString(version, attribute);
      }
    }
    catch (Exception e) {
      logger('E', {"Exception in versionUpdate(): ${e}"});
    }
  }
  else {
    ok = true;
    // Capturing the situation where Git Repo monitoring has been turned off and a version update is still captured in the driver attribute
    if(devVer != verCur.desc) {
      logger('D', "versionUpdate: Device driver version does not match the code, updating it now");
      devOk = attributeUpdateString(verCur.desc, attribute);
    }
  }
  return (ok);
}

// DNI ------------------------------------------------------------------------------------------------------------------------

private Map dniIsValid(String str) {
  //
  // Return null if not valid
  // otherwise return both hex and canonical version
  //
  List<Integer> val = [];

  try {
    List<String> token = str.replaceAll(" ", "").tokenize(".:");
    if (token.size() == 4) {
      // Regular IPv4
      token.each {
        Integer num = Integer.parseInt(it, 10);
        if (num < 0 || num > 255) throw new Exception();
        val.add(num);
      }
    }
    else if (token.size() == 6) {
      // Regular MAC
      token.each {
        Integer num = Integer.parseInt(it, 16);
        if (num < 0 || num > 255) throw new Exception();
        val.add(num);
      }
    }
    else if (token.size() == 1) {
      // Hexadecimal IPv4 or MAC
      str = token[0];
      if ((str.length() != 8 && str.length() != 12) || str.replaceAll("[a-fA-F0-9]", "").length()) throw new Exception();
      for (Integer idx = 0; idx < str.length(); idx += 2) val.add(Integer.parseInt(str.substring(idx, idx + 2), 16));
    }
  }
  catch (Exception ignored) {
    val.clear();
  }

  Map dni = null;

  if (val.size() == 4) {
    dni = [:];
    dni.hex = sprintf("%02X%02X%02X%02X", val[0], val[1], val[2], val[3]);
    dni.canonical = sprintf("%d.%d.%d.%d", val[0], val[1], val[2], val[3]);
  }

  if (val.size() == 6) {
    dni = [:];
    dni.hex = sprintf("%02X%02X%02X%02X%02X%02X", val[0], val[1], val[2], val[3], val[4], val[5]);
    dni.canonical = sprintf("%02x:%02x:%02x:%02x:%02x:%02x", val[0], val[1], val[2], val[3], val[4], val[5]);
  }

  return (dni);
}

// ------------------------------------------------------------

private String dniUpdate() {
  //
  // Get the gateway address (either MAC or IP) from the properties and, if valid and not done already, update the driver DNI
  // Return "error") invalid address entered by the user
  //           null) same address as before
  //             "") new valid address
  //
  logger('D', "dniUpdate()");

  String error = "";
  String attribute = "mac";
  String setting = gatewayMacAddress();

  Map dni = dniIsValid(setting);
  if (dni) {

    if (cachedString(attribute) == dni.canonical) {
      // The address hasn't changed: we do nothing
      error = null;
    }
    else {
      // Save the new address as an attribute for later comparison
      attributeUpdateString(dni.canonical, attribute);

      // Update the DNI
      device.setDeviceNetworkId(dni.hex);
    }
  }
  else {
    error = "\"${setting}\" is not a valid MAC or IP address";
  }

  return (error);
}


def nsCallback(resp, data) {
  logger('D', "in callback")

  // test change

  def jSlurp = new JsonSlurper()
  Map ipData = (Map)jSlurp.parseText((String)resp.data)
  def String newIP = ipData.Answer.data[0]
  sendEvent(name:"dynamicIPResult", value:ipData.Answer.data[0])

  // now compare ip to our own and if different reset and log
  if ((newIP != null) && (newIP != ""))
  {
      def String currentIP = settings.macAddress
      logger('I', {"Comparing resolved IP: $newIP to $currentIP"})
      
      if (currentIP != newIP)
      {
          logger('I', "IP address has Changed !!! Resetting DNI !")
          Map dni = dniIsValid(newIP);
          // Update Device Network ID
          logger('D', {"got back dni = $dni"})
          if (dni) 
          { 
            device.updateSetting("macAddress", [type: "string", value: dni.canonical]);
            dniUpdate();
            resyncSensors();
          }
      }
        
  }
}

void DNSCheckCallback() {
  logger('I', "Dns Update Check Callback Startup")
  updated()
}



// Logging --------------------------------------------------------------------------------------------------------------------

void logDebugOff() {
  //
  // runIn() callback to disable "Debug" logging after 30 minutes
  // Cannot be private
  //
  logger('I', "Debug logging auto-disabled")
  device.updateSetting("loggingLevel", [value: "3", type: "enum"]);
  updateCachedLoggingLevel()
}

// ------------------------------------------------------------

private void logData(Map data) {
  //
  // Log all data received from the wifi gateway
  // Used only for diagnostic/debug purposes
  //
  if (getCachedLoggingLevel() >= 5) {
    data.each {
      logger('T', "$it.key = $it.value");
    }
  }
}

// Device Status --------------------------------------------------------------------------------------------------------------

private Boolean devStatus(String str = null, String color = null) {
  if (str) {
    if (color) str = "<font style='color:${color}'>${str}</font>";

    return (attributeUpdateString(str, "status"));
  }

  if (cachedString("status") != null) {
    device.deleteCurrentState("status");
    getAttrCache().remove("status")
    return (true);
  }

  return (false);
}

// Sensor handling ------------------------------------------------------------------------------------------------------------

String sensorIdToDni(String sid) {
  String pid = device.getId().concat("-"); 

  if (sid.startsWith(pid)) return (sid);
  return (pid.concat(sid));
}

// ------------------------------------------------------------

String sensorDniToId(String dni) {
  String pid = device.getId().concat("-"); 

  if (dni.startsWith(pid)) return (dni.substring(pid.size()));
  return (dni); 
}

// ------------------------------------------------------------

/*
 *            Outdoor
 *            Temperature                 Wind
 *            & Humidity    Rain          & Solar
 *            ------------- ------------- --------------
 *      WH26  X
 *      WH40                X
 *      WH68                              X
 *      WH80  X                           X
 * WH65/WH69  X             X             X
 * WS90       X             X             X
 *
 */

private void sensorMapping(Map data) {
  //
  // Remap sensors, boundling or decoupling devices, depending on what's present
  //
  //                     0       1       2       3       4       5       6       7       8       9       10      11        12     13
  String[] sensorMap =  ["WH69", "WH25", "WH26", "WH31", "WH40", "WH41", "WH51", "WH55", "WH57", "WH80", "WH34", "WFST", "WN35", "WS90"];

  logger('D', "sensorMapping()");

  // Detect outdoor sensors by their battery signature
  Boolean wh26 = data.containsKey("wh26batt");
  Boolean wh40 = data.containsKey("wh40batt");
  Boolean wh68 = data.containsKey("wh68batt");
  Boolean wh80 = data.containsKey("wh80batt");
  Boolean wh69 = data.containsKey("wh65batt");
  Boolean ws90 = data.containsKey("ws90batt") || data.containsKey("wh90batt");

  // Count outdoor sensor
  Integer outdoorSensors = 0;
  if (wh26) outdoorSensors += 1;
  if (wh40) outdoorSensors += 1;
  if (wh68) outdoorSensors += 1;
  if (wh80) outdoorSensors += 1;

  // A bit of sanity check
  if (wh69 && outdoorSensors) logger('W', "The PWS should be the only outdoor sensor");
  if (ws90 && outdoorSensors) logger('W', "The PWS should be the only outdoor sensor");
  if (wh80 && wh26) logger('W', "Both WH80 and WH26 are present with overlapping sensors");

  if (wh80) {
    //
    // WH80 (includes temp & humidity)
    //
    sensorMap[2] = sensorMap[9];
  }

  if (wh69) {
    //
    // We have a real WH65/WH69 PWS
    //
    sensorMap[2] = sensorMap[0];
    sensorMap[4] = sensorMap[0];
    sensorMap[9] = sensorMap[0];
  }
  if (ws90) {
    //
    // We have a real ws90 PWS
    //
    sensorMap[2] = sensorMap[13];  // Outdoor temp/humidity → WS90
    // If a separate rain sensor (WH40/WH40H) is present, keep it separate
    if (!wh40) {
      // Only remap rain to WS90 if no separate rain sensor is present
      sensorMap[4] = sensorMap[13];
    }
    sensorMap[9] = sensorMap[13];  // Wind sensor → WS90
  }
  else if (bundleOutdoorSensors() && outdoorSensors > 1) {
    //
    // We are requested to bundle outdoor sensors and we have more than 1
    //
    sensorMap[2] = sensorMap[0];
    sensorMap[4] = sensorMap[0];
    sensorMap[9] = sensorMap[0];

    device.updateDataValue("sensorBundled", "WH69");
  }

  // Save the mapping in the state variables
  device.updateDataValue("sensorMap", sensorMap.toString());
}

// ------------------------------------------------------------

String sensorModel(Integer id) {

  // assert (id >= 0 && id <= 10);

  //                      0     1     2     3     4     5     6     7     8     9     10    11    12
  // String sensorMap = "[WH69, WH25, WH26, WH31, WH40, WH41, WH51, WH55, WH57, WH80, WH34, WFST, WN35]";
  //
  String sensorMap = device.getDataValue("sensorMap");

  id *= 6;

  return (sensorMap.substring(id + 1, id + 5));
}

// ------------------------------------------------------------

private String sensorName(Integer id, Integer channel) {

  Map sensorId = ["WH69": "PWS Sensor",
                  "WH25": "Indoor Ambient Sensor",
                  "WH26": "Outdoor Ambient Sensor",
                  "WH31": "Ambient Sensor",
                  "WH40": "Rain Gauge Sensor",
                  "WH41": "Air Quality Sensor",
                  "WH51": "Soil Moisture Sensor",
                  "WH55": "Water Leak Sensor",
                  "WH57": "Lightning Detection Sensor",
                  "WH80": "Wind Solar Sensor",
                  "WH34": "Water/Soil Temperature Sensor",
                  "WFST": "WeatherFlow Station",
                  "WN35": "Leaf Wetness Sensor"];

  String model = sensorId."${sensorModel(id)}";

  return (channel? "${model} ${channel}": model);
}

// ------------------------------------------------------------

private String sensorId(Integer id, Integer channel) {

  String model = sensorModel(id);

  return (channel? "${model}_CH${channel}": model);
}

// ------------------------------------------------------------

private Boolean sensorIsBundled(Integer id) {

  return (sensorModel(id) == device.getDataValue("sensorBundled"));
}

// ------------------------------------------------------------

private void sensorGarbageCollect() {
  //
  // Match the new (soon to be created) sensor list with the existing one
  // and delete sensors in the existing list that are not in the new one
  //
  ArrayList<String> sensorList = [];

  String value = device.getDataValue("sensorList");
  if (value) sensorList = value.tokenize("[, ]");

  List<com.hubitat.app.ChildDeviceWrapper> list = getChildDevices();
  if (list) list.each {
    String dni = it.getDeviceNetworkId();
    if (sensorList.contains(sensorDniToId(dni)) == false) deleteChildDevice(dni);
  }
}

// ------------------------------------------------------------

private Boolean sensorEnumerate(String key, String value, Integer id = null, Integer channel = null) {
  //
  // Enumerate sensors needed for the current data
  //
  if (id) {
    String sid = sensorId(id, channel);

    ArrayList<String> sensorList = [];

    value = device.getDataValue("sensorList");
    if (value) sensorList = value.tokenize("[, ]");

    if (sensorList.contains(sid) == false) {
      sensorList.add(sid);

      // Save the list in the state variables
      device.updateDataValue("sensorList", sensorList.toString());
    }
  }

  return (true);
}

// ------------------------------------------------------------

private Boolean sensorUpdate(String key, String value, Integer id = null, Integer channel = null) {
  //
  // If not present, add the child sensor corresponding to the specified id/channel
  // and, if child sensor is present, update the attribute
  //
  // If id is null we broadcast to all children
  //
  Boolean updated = false;

  try {
    if (id) {
      String dni = sensorIdToDni(sensorId(id, channel));

      com.hubitat.app.ChildDeviceWrapper sensor = getChildDevice(dni);
      if (sensor == null) {
        //
        // Support for sensors with legacy DNI (without the parent ID)
        //
        sensor = getChildDevice(sensorDniToId(dni)); 
        if (sensor) {
          // Found existing sensor with legacy name: update it
          sensor.setDeviceNetworkId(dni);
        }
        else {
          //
          // Sensor doesn't exist: we need to create it
          //
          sensor = addChildDevice("Ecowitt RF Sensor", dni, [name: sensorName(id, channel), isComponent: true]);
          if (sensor && sensorIsBundled(id)) sensor.updateDataValue("isBundled", "true");
        }

        devStatus();
      }

      if (sensor) updated = sensor.attributeUpdate(key, value);
    }
    else {
      // We broadcast to all children
      List<com.hubitat.app.ChildDeviceWrapper> list = getChildDevices();
      if (list) list.each { if (it.attributeUpdate(key, value)) updated = true; }
    }
  }
  catch (Exception e) {
    if (e instanceof com.hubitat.app.exception.UnknownDeviceTypeException) {
      logger('E', "Unable to create child sensor device. Please make sure the \"ecowitt_sensor.groovy\" driver is installed.");
      devStatus("Unable to create child sensor device. Please make sure the \"ecowitt_sensor.groovy\" driver is installed", "red");
    }
    else logger('E', {"Exception in sensorUpdate(${id}, ${channel}): ${e}"});
  }

  return (updated);
}

// Attribute handling ---------------------------------------------------------------------------------------------------------

private Integer matchedChannel() {
  return java.util.regex.Matcher.lastMatcher.group(1).toInteger()
}

@Field static final Map SENSOR_ID_NAMES = [
  (1):"WH25 (Indoor)", (2):"WH26 (Outdoor Temp/Hum)", (3):"WH31 (Multi-ch Ambient)",
  (4):"WH40 (Rain Gauge)", (5):"WH45 (Air Quality)", (6):"WH51 (Soil Moisture)",
  (7):"WH55 (Water Leak)", (8):"WH57 (Lightning)", (9):"WH80/WS90 (Wind/Solar)",
  (10):"WH34 (Soil/Water Temp)", (11):"WFST (WeatherFlow)", (12):"WN35 (Leaf Wetness)",
  (13):"WS90_RAIN (Piezo Rain)"
]

private Set buildEnabledSensorIds() {
  String raw = settings.enabledSensors
  if (raw == null || raw.trim() == "") {
    // Not configured yet: all enabled (backward compatible)
    return new HashSet([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13])
  }
  Set ids = new HashSet()
  Set enabled = raw.tokenize(",").collect { it.trim().toUpperCase() } as Set
  if (enabled.contains("WH25")) ids.add(1)
  if (enabled.contains("WH26")) ids.add(2)
  if (enabled.contains("WH31")) ids.add(3)
  if (enabled.contains("WH40")) ids.add(4)
  if (enabled.contains("WH45") || enabled.contains("WH41")) ids.add(5)
  if (enabled.contains("WH51")) ids.add(6)
  if (enabled.contains("WH55")) ids.add(7)
  if (enabled.contains("WH57")) ids.add(8)
  if (enabled.contains("WH80") || enabled.contains("WS90")) ids.add(9)
  if (enabled.contains("WH34")) ids.add(10)
  if (enabled.contains("WFST")) ids.add(11)
  if (enabled.contains("WN35")) ids.add(12)
  if (enabled.contains("WS90_RAIN")) ids.add(13)
  return ids
}

private Set getEnabledSensorIds() {
  if (cachedEnabledSensorIds != null) return cachedEnabledSensorIds
  cachedEnabledSensorIds = buildEnabledSensorIds()
  return cachedEnabledSensorIds
}

private Boolean attributeUpdateString(String val, String attribute) {
  //
  // Only update "attribute" if different
  // Return true if "attribute" has actually been updated/created
  //
  Map cache = getAttrCache()
  String current = cachedString(attribute)
  if (current != val) {
    logger('D', {"attributeUpdateString(${attribute} : ${val}) - current value: ${current}"});
    sendEvent(name: attribute, value: val);
    cache[attribute] = val
    return (true);
  }

  return (false);
}

private Boolean attributeUpdateNumber(Number val, String attribute) {
  //
  // Only update "attribute" if different
  // Return true if "attribute" has actually been updated/created
  //
  Map cache = getAttrCache()
  Number current = cachedNumber(attribute)
  if (current != val) {
    logger('D', {"attributeUpdateNumber(${attribute} : ${val}) - current value: ${current}"});
    sendEvent(name: attribute, value: val);
    cache[attribute] = val
    return (true);
  }

  return (false);
}

// ------------------------------------------------------------

private Boolean attributeUpdate(Map data, Closure sensor) {
  //
  // Dispatch parent/child attribute changes to hub
  // Uses O(1) map lookup for known string keys and gated regex for multi-channel sensors
  //
  Boolean updated = false;
  Set enabledIds = getEnabledSensorIds();

  logger('T', {"Data size: ${data.size()}"});

  data.each {

    // ---- O(1) lookup for known single-sensor string keys ----
    Integer sensorId = (Integer) SENSOR_KEY_MAP[it.key];
    if (sensorId != null) {
      if (enabledIds.contains(sensorId)) {
        updated = sensor(it.key, it.value, sensorId);
      }
      return; // next key
    }

    // ---- Gateway-local keys (custom processing logic) ----
    switch (it.key) {
    //
    // Gateway attributes
    //

    case "interval":
      // added in EcoWitt firmware 2.4.0 as the time between custom data feeds
      updated = attributeUpdateNumber(it.value.toInteger(), "updateInterval");
      return;

    case "model":
      // Eg: model = GW1000_Pro
      updated = attributeUpdateString(it.value, "model");
      return;

    case "stationtype":
      // Eg: firmware = GW1000B_V1.5.7
      Map ver = versionExtract(it.value);
      if (ver) it.value = ver.desc;
      updated = attributeUpdateString(it.value, "firmware");
      return;

    case "freq":
      // Eg: rf = 915M
      updated = attributeUpdateString(it.value, "rf");
      return;

    case "PASSKEY":
      // Eg: passkey = 15CF2C872932F570B34AC469540099A4
      updated = attributeUpdateString(it.value, "passkey");
      return;

    case "runtime":
      if (it.value.isInteger() && settings.reportTimers) { updated = attributeUpdateNumber(it.value.toInteger(), "runtime"); }
      return;

    case "dateutc":
      if (settings.reportTimers) {
        String prevDateutc = device.getDataValue("dateutc")
        if (prevDateutc != it.value) {
          device.updateDataValue("dateutc", it.value);
        }
        updated = true;
      }
      return;

    case "gain30_piezo":
      // we won't handle this one for now, need to work out what it relates to...
      updated = true;
      return;

    case "endofdata":
      // Special key to notify all drivers (parent and children) of end-of-data status
      updated = sensor(it.key, it.value);
      // Last thing we do on the driver
      if (settings.reportTimers && attributeUpdateString(it.value, "lastUpdate")) updated = true;
      return;
    }

    // ---- Multi-channel sensors (regex, gated by enabledSensors preference) ----

    //
    // Leaf Wetness Sensor (WN35)
    //
    if (enabledIds.contains(12)) {
      switch (it.key) {
      case RE_LEAF_BATT:
      case RE_LEAFWETNESS:
        updated = sensor(it.key, it.value, 12);
        return;
      }
    }

    //
    // Multi-channel Water Leak Sensor (WH55)
    //
    if (enabledIds.contains(7)) {
      switch (it.key) {
      case RE_LEAKBATT:
      case RE_LEAK_CH:
        updated = sensor(it.key, it.value, 7, matchedChannel());
        return;
      }
    }

    //
    // Water/Soil Temperature Sensor (WH34)
    //
    if (enabledIds.contains(10)) {
      switch (it.key) {
      case RE_TF_BATT:
      case RE_TF_CH:
        updated = sensor(it.key, it.value, 10, matchedChannel());
        return;
      }
    }

    //
    // Multi-channel Ambient Sensor (WH31)
    //
    if (enabledIds.contains(3)) {
      switch (it.key) {
      case RE_BATT_1_8:
      case RE_TEMP_1_8:
      case RE_HUMIDITY_1_8:
        updated = sensor(it.key, it.value, 3, matchedChannel());
        return;
      }
    }

    //
    // Multi-channel Soil Moisture Sensor (WH51)
    //
    if (enabledIds.contains(6)) {
      switch (it.key) {
      case RE_SOILBATT:
      case RE_SOILMOISTURE:
      case RE_SOILAD:
        updated = sensor(it.key, it.value, 6, matchedChannel());
        return;
      }
    }

    //
    // Multi-channel Air Quality Sensor (WH41)
    //
    if (enabledIds.contains(5)) {
      switch (it.key) {
      case RE_PM25BATT:
      case RE_PM25_CH:
      case RE_PM25_AVG:
        updated = sensor(it.key, it.value, 5, matchedChannel());
        return;
      }
    }

    //
    // WeatherFlow Station (WFST)
    //
    if (enabledIds.contains(11)) {
      switch (it.key) {
      case RE_BATT_WF:
      case RE_TEMPF_WF:
      case RE_HUMIDITY_WF:
      case RE_BAROMRELIN_WF:
      case RE_BAROMABSIN_WF:
      case RE_LIGHTNING_WF:
      case RE_LIGHTNING_TIME_WF:
      case RE_LIGHTNING_ENERGY_WF:
      case RE_LIGHTNING_NUM_WF:
      case RE_UV_WF:
      case RE_SOLAR_WF:
      case RE_RAINRATE_WF:
      case RE_EVENTRAIN_WF:
      case RE_HOURLYRAIN_WF:
      case RE_DAILYRAIN_WF:
      case RE_WEEKLYRAIN_WF:
      case RE_MONTHLYRAIN_WF:
      case RE_YEARLYRAIN_WF:
      case RE_TOTALRAIN_WF:
      case RE_WINDDIR_WF:
      case RE_WINDDIR_AVG_WF:
      case RE_WINDSPEED_WF:
      case RE_WINDSPD_AVG_WF:
      case RE_WINDGUST_WF:
      case RE_MAXGUST_WF:
        updated = sensor(it.key, it.value, 11, matchedChannel());
        return;
      }
    }

    logger('D', {"Unrecognized attribute: ${it}"});
  }

  return (updated);
}

// Commands -------------------------------------------------------------------------------------------------------------------

void resyncSensors() {
  //
  // This will trigger a sensor remapping and cleanup
  //
  try {
    logger('D', {"resyncSensors()"});

    if (dniIsValid(device.getDeviceNetworkId())) {
      // We have a valid gateway dni
      devStatus("Sensor sync pending", "blue");

      device.updateDataValue("sensorResync", "true");
    }
  }
  catch (Exception e) {
    logger('E', {"Exception in resyncSensors(): ${e}"});
  }
}

// Driver lifecycle -----------------------------------------------------------------------------------------------------------

void installed() {
  //
  // Called once when the driver is created
  //
  try {
    logger('D', "installed()");

    Map dni = dniIsValid(device.getDeviceNetworkId());
    if (dni) {
      device.updateSetting("macAddress", [type: "string", value: dni.canonical]);
      updated();
    }

  }
  catch (Exception e) {
    logger('E', {"Exception in installed(): ${e}"});
  }
}

// ------------------------------------------------------------

void updated() {
  //
  // Called everytime the user saves the driver preferences
  //
  // Cache the logging level before any logging calls
  logger('I', "updated()");
  updateCachedLoggingLevel()
  try {

    // Clear previous states
    state.clear();
    invalidateCache();

    // Invalidate metric cache on children (unit system may have changed)
    getChildDevices()?.each { child ->
      if (child.respondsTo("invalidateMetricCache")) child.invalidateMetricCache()
    }

    // Rebuild and cache enabled sensor IDs
    cachedEnabledSensorIds = null
    Set enabledIds = buildEnabledSensorIds()
    cachedEnabledSensorIds = Collections.unmodifiableSet(enabledIds)
    List enabledNames = enabledIds.sort().collect { SENSOR_ID_NAMES[it] }
    logger('I', "Enabled sensors: ${enabledNames.join(', ')}")

    // Unschedule possible previous runIn() calls
    unschedule();

    // lgk if ddns name resolve this first and do ip check before dniupdatE.. ALSO schedule the re-check.
    def String ddnsname = settings.DDNSName
    def Number ddnsupdatetime = settings.DDNSRefreshTime
                                          
    logger('D', {"DDNS Name = $ddnsname"})
    logger('D', {"DDNS Refresh Time = $ddnsupdatetime"})
                                          
    if ((ddnsname != null) && (ddnsname != "")) {
      logger('D', {"Got ddns name $ddnsname"})
      // now resolve

      Map params = [
        uri: "https://8.8.8.8/resolve?name=$ddnsname&type=A",
        contentType: "text/plain",
        timeout: 20
      ]

      logger('D', {"calling dns Update url = $params"})
      asynchttpGet("nsCallback", params)
    }
    // now schedule next run of update
    if ((ddnsupdatetime != null) && (ddnsupdatetime != 00)) {
      def thesecs = ddnsupdatetime * 3600
      logger('I', {"Rescheduling IP Address Check to run again in $thesecs seconds."})
      runIn(thesecs, "DNSCheckCallback");
    }

    // Update Device Network ID
    String error = dniUpdate();
    if (error == null) {
      // The gateway dni hasn't changed: we set OK only if a resync sensors is not pending
      if (device.getDataValue("sensorResync")) devStatus("Sensor sync pending", "blue");
      else devStatus();
    }
    else if (error != "") devStatus(error, "red");
    else resyncSensors();

    // Update driver version now and every Sunday @ 2am, if we are monitoring Git
    versionUpdate();
    if(monitorGitVersion()) {
      schedule("0 0 2 ? * 1 *", versionUpdate);
    }

    // Turn off debug log in 30 minutes
    if (cachedLoggingLevel >= 4) runIn(1800, logDebugOff);

    // lgk get rid of now unused time attribute
    device.deleteCurrentState("time")
  } // TODO: most exceptions should not be caught, the hub handles them better than this
  catch (Exception e) {
    logger('E', {"Exception in updated(): ${e}"});
  }
}

// ------------------------------------------------------------

void uninstalled() {
  //
  // Called once when the driver is deleted
  //
  try {
    // Delete all children
    List<com.hubitat.app.ChildDeviceWrapper> list = getChildDevices();
    if (list) list.each { deleteChildDevice(it.getDeviceNetworkId()); }

    logger('D', "uninstalled()");
  }
  catch (Exception e) {
    logger('E', {"Exception in uninstalled(): ${e}"});
  }
}

void uninstalledChildDevice(String dni) {
  //
  // Called by the children to notify the parent they are being uninstalled
  //
}

// ------------------------------------------------------------

def forwardData(String msg) {

    if(forwardAddress != null && forwardAddress != "") {
      logger('D', {"forwardData() - forwarding to IP ${forwardAddress}, Port ${forwardPort} and Path ${forwardPath}"});
      logger('D', {"forwardData() - data = ${msg}"});
      def bodyForm = msg;
      def postParams = [:];
      def headers = [:];
      headers.put("accept", "application/x-www-form-urlencoded");
    
      postParams = [
          uri: "http://${forwardAddress}:${forwardPort}",
          path: forwardPath,
          headers: headers,
          contentType: "application/x-www-form-urlencoded",
          body : bodyForm,
          ignoreSSLIssues: true
      ];
           
      try {
          asynchttpPost(postParams);
      }
      catch(Exception e)
      {
          logger('E', {"forwardData: Exception ${e}"})   
      }
    }
    
}

// ------------------------------------------------------------
void parse(String msg) {
  //
  // Called everytime a POST message is received from the WiFi Gateway
    logger('D', "parse()")

    // Parse POST message
    Map data = parseLanMessage(msg);

    // Save only the body and discard the header
    String body = data["body"];

    // Build a map with one key/value pair for each field we receive
    data = [:];
    body.split("&").each {
      String[] keyValue = it.split("=");
      data[keyValue[0]] = (keyValue.size() > 1)? keyValue[1]: "";
    }

    // "dewPoint" and "heatIndex" are based on "tempf" and "humidity"
    // for them to be calculated properly, in "data", "humidity", if present, must come after "tempf"

    // "windchill" is based on "tempf" and "windspeedmph"
    // for it to be calculated properly, in "data", "windspeedmph", if present, must come after "tempf"

    // "aqi" is based on "pm25_24h_co2" and "pm10_24h_co2"
    // for it to be calculated properly, in "data", "pm10_24h_co2", if present, must come after "pm25_24h_co2"

    // Inject a special key (at the end of the data map) to notify all the driver of end-of-data status. Value is local time
    def now = new Date().format('yyyy-MM-dd h:mm a',location.timeZone)
    data["endofdata"] = now

    logData(data);

    if (device.getDataValue("sensorResync")) {
      // We execute this block only the first time we receive data from the wifi gateway
      // or when the user presses the "Resynchronize Sensors" command
      device.removeDataValue("sensorResync");
 
      // (Re)create sensor map
      device.removeDataValue("sensorBundled");      
      device.removeDataValue("sensorMap");
      sensorMapping(data);

      // (Re)create sensor list
      device.removeDataValue("sensorList");
      attributeUpdate(data, this.&sensorEnumerate);

      // Match the new (soon to be created) sensor list with the existing one
      // and delete sensors in the existing list that are not in the new one
      sensorGarbageCollect();

      // Clear pending status and start processing data
      devStatus();
    }

    attributeUpdate(data, this.&sensorUpdate);
    
    //Driver Version Updates

    // If the driver has been updated on the HE hub, check that this is reflected in the driver attribute
    // If the current driver value is empty or different, run the version update to record the correct details
    // if(curVer == null || curVer == "" || !(curVer.startsWith(versionExtract(version()).desc))) {
    //   //TODO it is being called on every data ingestion, optimize to call only once after driver update
    //   logger('D', "Driver on HE Hub updated, running versionUpdate() to update the driver attribute");
    //   versionUpdate();
    // }
    
    // Forward the data on, if configured for the Gateway
    forwardData(body);
}

// Recycle bin ----------------------------------------------------------------------------------------------------------------

/*

synchronized(this) {

}


@Field static java.util.concurrent.Semaphore mutex = new java.util.concurrent.Semaphore(1)

if (!mutex.tryAcquire())

mutex.release()


*/

// EOF ------------------------------------------------------------------------------------------------------------------------
