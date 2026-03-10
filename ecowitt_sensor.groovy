/**
 * Driver:     Ecowitt RF Sensor
 * Author:     Simon Burke (Original author Mirco Caramori - github.com/mircolino)
 * Repository: https://github.com/sburke781/ecowitt
 * Import URL: https://raw.githubusercontent.com/sburke781/ecowitt/main/ecowitt_sensor.groovy
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
 * Change Log: shared with ecowitt_gateway.groovy

* lgk add srain_piezo and raining = true or false, also capacitorVoltage and firmware versions, stick the raing on the rain device and cap voltate and firmware version on the wind device
* lgk fixed missing break statement when processing srain_piezo and raining attributes
* ikishk and lgk - updated parsing of soil moisture detail to cater for more channels
* lgk - also added soilad not sure what it is used for as it appears to be some kinda variable for cailbration see here.
* https://www.reddit.com/r/myweatherstation/comments/18ngx4c/ecowitt_soil_moisture_showing_ad_in_additional_to/
* lgk - added vpd attribute
*/

public static String gitHubUser() { return "sburke781"; }
public static String gitHubRepo() { return "ecowitt"; }
public static String gitHubBranch() { return "main"; }

import groovy.transform.Field

// In-memory tracking instead of state variables for sensor presence detection
@Field static final java.util.concurrent.ConcurrentHashMap<String, Map> sensorTracker = new java.util.concurrent.ConcurrentHashMap()

// In-memory attribute cache to reduce device.currentValue() database reads
@Field static final java.util.concurrent.ConcurrentHashMap attributeCache = new java.util.concurrent.ConcurrentHashMap()

// Cached unitSystemIsMetric per device to reduce cross-device parent calls (~15-20 per cycle → 1)
// Invalidated by parent gateway on updated()
@Field static final java.util.concurrent.ConcurrentHashMap metricCache = new java.util.concurrent.ConcurrentHashMap()

// Cached logging level per device to avoid conversion on every log call
@Field static final java.util.concurrent.ConcurrentHashMap loggingLevelCache = new java.util.concurrent.ConcurrentHashMap()

// Pre-compiled regex patterns for sensor key matching
@Field static final java.util.regex.Pattern RE_S_BATT_1_8 = ~/batt[1-8]/
@Field static final java.util.regex.Pattern RE_S_WS90CAP_VOLT = ~/ws90cap_volt[1-8]/
@Field static final java.util.regex.Pattern RE_S_BAROMRELIN_WF = ~/baromrelin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_BAROMABSIN_WF = ~/baromabsin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_RAINRATE_WF = ~/rainratein_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_SRAIN_PIEZO = ~/srain_piezo[1-8]/
@Field static final java.util.regex.Pattern RE_S_EVENTRAIN_WF = ~/eventrainin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_HOURLYRAIN_WF = ~/hourlyrainin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_DAILYRAIN_WF = ~/dailyrainin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_WEEKLYRAIN_WF = ~/weeklyrainin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_MONTHLYRAIN_WF = ~/monthlyrainin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_YEARLYRAIN_WF = ~/yearlyrainin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_TOTALRAIN_WF = ~/totalrainin_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_PM25_CH = ~/pm25_ch[1-4]/
@Field static final java.util.regex.Pattern RE_S_PM25_AVG = ~/pm25_avg_24h_ch[1-4]/
@Field static final java.util.regex.Pattern RE_S_LIGHTNING_WF = ~/lightning_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_LIGHTNING_NUM_WF = ~/lightning_num_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_LIGHTNING_TIME_WF = ~/lightning_time_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_UV_WF = ~/uv_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_SOLAR_WF = ~/solarradiation_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_WS90_VER = ~/ws90_ver[1-8]/
@Field static final java.util.regex.Pattern RE_S_WINDDIR_WF = ~/winddir_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_WINDDIR_AVG_WF = ~/winddir_avg10m_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_WINDSPEED_WF = ~/windspeedmph_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_WINDSPD_AVG_WF = ~/windspdmph_avg10m_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_WINDGUST_WF = ~/windgustmph_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_MAXGUST_WF = ~/maxdailygust_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_VPD = ~/vpd[1-8]/
@Field static final java.util.regex.Pattern RE_S_PM25BATT = ~/pm25batt[1-4]/
@Field static final java.util.regex.Pattern RE_S_LEAKBATT = ~/leakbatt[1-4]/
@Field static final java.util.regex.Pattern RE_S_HUMIDITY_WF = ~/humidity_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_HUMIDITY_1_8 = ~/humidity[1-8]/
@Field static final java.util.regex.Pattern RE_S_TEMPF_WF = ~/tempf_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_TEMP_1_8 = ~/temp[1-8]f/
@Field static final java.util.regex.Pattern RE_S_TF_CH = ~/tf_ch[1-8]/
@Field static final java.util.regex.Pattern RE_S_LEAK_CH = ~/leak_ch[1-4]/
@Field static final java.util.regex.Pattern RE_S_SOILMOISTURE = ~/soilmoisture([1-9]|1[0-6])$/
@Field static final java.util.regex.Pattern RE_S_SOILAD = ~/soilad([1-9]|1[0-6])$/
@Field static final java.util.regex.Pattern RE_S_LEAFWETNESS = ~/leafwetness_ch[1-8]/
@Field static final java.util.regex.Pattern RE_S_LIGHTNING_ENERGY_WF = ~/lightning_energy_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_BATT_WF = ~/batt_wf[1-8]/
@Field static final java.util.regex.Pattern RE_S_LEAF_BATT = ~/leaf_batt[1-8]/
@Field static final java.util.regex.Pattern RE_S_SOILBATT = ~/soilbatt([1-9]|1[0-6])$/
@Field static final java.util.regex.Pattern RE_S_TF_BATT = ~/tf_batt[1-8]/

// Pre-compiled regex for HTML template variable substitution
@Field static final java.util.regex.Pattern RE_HTML_VAR = ~/\$\{([^}]+)\}/

// Cached SimpleDateFormat for epoch-to-local time conversion
@Field static final java.text.SimpleDateFormat cachedDateFormat = new java.text.SimpleDateFormat()

metadata {
  definition(name: "Ecowitt RF Sensor", namespace: "ecowitt", author: "Simon Burke", importUrl: "https://raw.githubusercontent.com/${gitHubUser()}/${gitHubRepo()}/${gitHubBranch()}/ecowitt_sensor.groovy") {
    capability "Sensor";

    capability "Battery";
    capability "Temperature Measurement";
    capability "Relative Humidity Measurement";
    capability "Pressure Measurement";
    capability "Ultraviolet Index";
    capability "Illuminance Measurement";
    capability "Water Sensor";
    capability "Carbon Dioxide Measurement";
    capability "Air Quality";

    command "clearAllStates";

 // attribute "battery", "number";                             // 0-100%
    attribute "batteryIcon", "number";                         // 0, 20, 40, 60, 80, 100
    attribute "batteryOrg", "number";                          // original/un-translated battery value returned by the sensor
      
    attribute "batterySolar", "number";                        // Only created/used for WS80/WS90, tracks capicator battery
    attribute "batterySolarIcon", "number";
    attribute "batterySolarOrg", "number";                     // original/un-translated battery value returned by the sensor

    attribute "batteryTemp", "number";                         //
    attribute "batteryTempIcon", "number";                     // Only created/used when a WH32 is bundled in a PWS
    attribute "batteryTempOrg", "number";                      //

    attribute "batteryRain", "number";                         //
    attribute "batteryRainIcon", "number";                     // Only created/used when a WH40 is bundled in a PWS
    attribute "batteryRainOrg", "number";                      //

    attribute "batteryWind", "number";                         //
    attribute "batteryWindIcon", "number";                     // Only created/used when a WH68/WH80 is bundled in a PWS
    attribute "batteryWindOrg", "number";                      //
    attribute "capacitorVoltage", "number";
    attribute "ws90Firmware", "string";

 // attribute "temperature", "number";                         // °F

 // attribute "humidity", "number";                            // 0-100%
    attribute "humidityAbs", "number";                         // oz/yd³ or g/m³ 
    attribute "dewPoint", "number";                            // °F - calculated using outdoor "temperature" & "humidity"
    attribute "heatIndex", "number";                           // °F - calculated using outdoor "temperature" & "humidity"
    attribute "heatDanger", "string";                          // Heat index danger level
    attribute "heatColor", "string";                           // Heat index HTML color
    attribute "simmerIndex", "number";                         // °F - calculated using outdoor "temperature" & "humidity"
    attribute "simmerDanger", "string";                        // Summer simmmer index danger level
    attribute "simmerColor", "string";                         // Summer simmer index HTML color

 // attribute "pressure", "number";                            // inHg - relative pressure corrected to sea-level
    attribute "pressureAbs", "number";                         // inHg - absolute pressure

    attribute "rainRate", "number";                            // in/h - rainfall rate
    attribute "rainEvent", "number";                           // in - rainfall in the current event
    attribute "rainHourly", "number";                          // in - rainfall in the current hour
    attribute "rainDaily", "number";                           // in - rainfall in the current day
    attribute "rainWeekly", "number";                          // in - rainfall in the current week
    attribute "rainMonthly", "number";                         // in - rainfall in the current month
    attribute "rainYearly", "number";                          // in - rainfall in the current year
    attribute "rainTotal", "number";                           // in - rainfall total since sensor installation
    attribute "raining", "string";                             // true false is it raining from srain_piezo
    
    attribute "pm25", "number";                                // µg/m³ - PM2.5 particle reading - current
    attribute "pm25_avg_24h", "number";                        // µg/m³ - PM2.5 particle reading - average over the last 24 hours
    attribute "pm10", "number";                                // µg/m³ - PM10 particle reading - current
    attribute "pm10_avg_24h", "number";                        // µg/m³ - PM10 particle reading - average over the last 24 hours

 // attribute "carbonDioxide", "number";                       // ppm - CO₂ concetration - current
    attribute "carbonDioxide_avg_24h", "number";               // ppm - CO₂ concetration - average over the last 24 hours

    attribute "aqi", "number";                                 // AQI (0-500)
    attribute "aqiDanger", "string";                           // AQI danger level
    attribute "aqiColor", "string";                            // AQI HTML color

    attribute "aqi_avg_24h", "number";                         // AQI (0-500) - average over the last 24 hours
    attribute "aqiDanger_avg_24h", "string";                   // AQI danger level - average over the last 24 hours
    attribute "aqiColor_avg_24h", "string";                    // AQI HTML color - average over the last 24 hours

 // attribute "water", "enum", ["dry", "wet"];                 // "dry" or "wet"
    attribute "waterMsg", "string";                            // dry) "Dry", wet) "Leak detected!"
    attribute "waterColor", "string";                          // dry) "ffffff", wet) "ff0000" to colorize the icon
      
    attribute "leafWetness", "number";                         // 0-100% leaf wetness

    attribute "lightningTime", "string";                       // Strike time - local time
    attribute "lightningDistance", "number";                   // Strike distance - km
    attribute "lightningEnergy", "number";                     // Strike energy - MJ/m
    attribute "lightningCount", "number";                      // Strike total count

 // attribute "ultravioletIndex", "number";                    // UV index (0-11+)
    attribute "ultravioletDanger", "string";                   // UV danger (0-2.9) Low, (3-5.9) Medium, (6-7.9) High, (8-10.9) Very High, (11+) Extreme
    attribute "ultravioletColor", "string";                    // UV HTML color

 // attribute "illuminance", "number";                         // lux
    attribute "solarRadiation", "number";                      // W/m²

    attribute "windDirection", "number";                       // 0-359°
    attribute "windCompass", "string";                         // NNE
    attribute "windDirection_avg_10m", "number";               // 0-359° - average over the last 10 minutes
    attribute "windCompass_avg_10m", "string";                 // NNE - average over the last 10 minutes
    attribute "windSpeed", "number";                           // mph
    attribute "windSpeed_avg_10m", "number";                   // mph - average over the last 10 minutes
    attribute "windGust", "number";                            // mph
    attribute "windGustMaxDaily", "number";                    // mph - max in the current day
    attribute "windChill", "number";                           // °F - calculated using outdoor "temperature" & "windSpeed"
    attribute "windDanger", "string";                          // Windchill danger level
    attribute "windColor", "string";                           // Windchill HTML color

    attribute "html", "string";                                //
    attribute "html1", "string";                               //
    attribute "html2", "string";                               // e.g. "<div>Temperature: ${temperature}°F<br>Humidity: ${humidity}%</div>"
    attribute "html3", "string";                               //
    attribute "html4", "string";                               //
      
    attribute "firmware", "string";                            // Used with sensors that have firmware

    attribute "status", "string";                              // Display current driver status

    attribute "orphaned", "enum", ["false", "true"];           // Whether or not the unbundled sensor is still receiving data from the gateway
    attribute "orphanedTemp", "enum", ["false", "true"];       // Whether or not the bundled WH32 is still receiving data from the gateway
    attribute "orphanedRain", "enum", ["false", "true"];       // Whether or not the bundled WH40 is still receiving data from the gateway
    attribute "orphanedWind", "enum", ["false", "true"];       // Whether or not the bundled WH68/WH80 sensor is still receiving data from the gateway    
    attribute "soilAD", "number";
    attribute "vpd", "number";                                 // Vapor Pressure Difference

 // command "settingsResetConditional";                        // Used for backward compatibility to reset device conditional preferences
  }

  preferences {
    input(name: "htmlEnabled", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Enable Tile HTML</font>", description: "<font style='font-size:12px; font-style: italic'>Rich multi-attribute dashboard tiles using html templates</font>", defaultValue: false);
    if (htmlEnabled || htmlEnabled == null) {
      input(name: "htmlTemplate", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Tile HTML Template(s)</font>", description: "<font style='font-size:12px; font-style: italic'>See <u><a href='https://github.com/${gitHubUser()}/${gitHubRepo()}/blob/${gitHubBranch()}/readme.md#templates' target='_blank'>documentation</a></u> for input formats</font>", defaultValue: "");
    }
    if (localAltitude != null) {
      input(name: "localAltitude", type: "string", title: "<font style='font-size:12px; color:#1a77c9'><u><a href='https://www.advancedconverter.com/map-tools/altitude-on-google-maps' target='_blank'>Altitude</a></u> to Correct Sea Level Pressure</font>", description: "<font style='font-size:12px; font-style: italic'>Examples: \"378 ft\" or \"115 m\"</font>", required: true);
    }
    if (voltageMin != null) {
      input(name: "voltageMin", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Empty Battery Voltage</font>", description: "<font style='font-size:12px; font-style: italic'>Sensor value when battery is empty</font>", required: true);
      input(name: "voltageMax", type: "string", title: "<font style='font-size:12px; color:#1a77c9'>Full Battery Voltage</font>", description: "<font style='font-size:12px; font-style: italic'>Sensor value when battery is full</font>", required: true);
    }
    if (calcDewPoint != null) {
      input(name: "calcDewPoint", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Calculate Dew Point & Absolute Humidity</font>", description: "<font style='font-size:12px; font-style: italic'>Temperature below which water vapor will condense & amount of water contained in a parcel of air</font>");
    }
    if (calcHeatIndex != null) {
      input(name: "calcHeatIndex", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Calculate Heat Index</font>", description: "<font style='font-size:12px; font-style: italic'>Perceived discomfort as a result of the combined effects of the air temperature and humidity</font>");
    }
    if (calcSimmerIndex != null) {
      input(name: "calcSimmerIndex", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Calculate Summer Simmer Index</font>", description: "<font style='font-size:12px; font-style: italic'>Similar to the Heat Index but using a newer and more accurate formula</font>");
    }
    if (calcWindChill != null) {
      input(name: "calcWindChill", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Calculate Wind-chill Factor</font>", description: "<font style='font-size:12px; font-style: italic'>Lowering of body temperature due to the passing-flow of lower-temperature air</font>");
    }
    if (calcWindCompass != null) {
      input(name: "calcWindCompass", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Calculate Wind Compass</font>", description: "<font style='font-size:12px; font-style: italic'>Calculates wind direction in text format: N,NE,SSE,SW,etc.</font>");
    }
    if (decsTemperature != null) {
      input(name: "decsTemperature", type: "number", title: "<font style='font-size:12px; color:#1a77c9'>Temperature decimals</font>", description: "<font style='font-size:12px; font-style: italic'>Enter a single digit number or -1 for no rounding</font>");
    }
    if (decsPressure != null) {
      input(name: "decsPressure", type: "number", title: "<font style='font-size:12px; color:#1a77c9'>Pressure decimals</font>", description: "<font style='font-size:12px; font-style: italic'>Enter a single digit number or -1 for no rounding</font>");
    }
    if (reportRainData != null) {
      input(name: "reportRainData", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Report Rain Data</font>", description: "<font style='font-size:12px; font-style: italic'>Disable/enable rain data reporting (you might wanna keep it only for dedicated rain sensors)</font>");
    }
    if (reportBatterySolar != null) {
      input(name: "reportBatterySolar", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Report Solar Battery Values</font>", description: "<font style='font-size:12px; font-style: italic'>Enable to receive solar battery readings. Disable to reduce events.</font>", defaultValue: false);
    }
    if (reportVPD != null) {
      input(name: "reportVPD", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Report VPD</font>", description: "<font style='font-size:12px; font-style: italic'>Enable/disable Vapor Pressure Deficit reporting</font>", defaultValue: false);
    }
    if (reportSoilAD != null) {
      input(name: "reportSoilAD", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Report Soil AD</font>", description: "<font style='font-size:12px; font-style: italic'>Enable/disable raw soil moisture millivolt (AD) reporting</font>", defaultValue: false);
    }
    input(name: 'loggingLevel', type: 'enum', title: 'Logging level', options: ['1':'Error', '2':'Warning', '3':'Info', '4':'Debug', '5':'Trace'], defaultValue: '3', required: true)
  }
}

/*
 * State variables used by the driver:
 *
 * sensor                      \
 * sensorTemp                   | null) not present, 0) waiting to receive data, 1) processing data
 * sensorRain                   |
 * sensorWind                  /
 *
 */

/*
 * Data variables used by the driver:
 *
 * "isBundled"                                                 // "true" if we are a bundled PWS (set by the parent at creation time)
 * "htmlTemplate"                                              // User template 0
 * "htmlTemplate1"                                             // User template 1
 * "htmlTemplate2"                                             // User template 2
 * "htmlTemplate3"                                             // User template 3
 * "htmlTemplate4"                                             // User template 4
 */

// Logging --------------------------------------------------------------------------------------------------------------------

private void logger(level, message) {
    int configuredLevel = getCachedLoggingLevel()
    switch (level) {
        case 'E': if (configuredLevel >= 1) { log.error(getLogMessage(message)) }; break
        case 'W': if (configuredLevel >= 2) { log.warn(getLogMessage(message)) }; break
        case 'I': if (configuredLevel >= 3) { log.info(getLogMessage(message)) }; break
        case 'D': if (configuredLevel >= 4) { log.debug(getLogMessage(message)) }; break
        case 'T': if (configuredLevel >= 5) { log.trace(getLogMessage(message)) }; break
    }
}

private String getLogMessage(message) {
  def text = (message instanceof Closure) ? message() : message
  return "${device.displayName}: ${text}"
}

private void updateCachedLoggingLevel() {
  loggingLevelCache.put(device.getId(), (settings.loggingLevel as String).toInteger())
}

private Integer getCachedLoggingLevel() {
  Integer cached = loggingLevelCache.get(device.getId())
  if (cached != null) return cached
  // Fallback if not cached (shouldn't happen, but safe default)
  Integer level = (settings.loggingLevel as String)?.toInteger() ?: 3
  loggingLevelCache.put(device.getId(), level)
  return level
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

// ------------------------------------------------------------

private Boolean devStatusIsError() {
  String str = cachedString("status")
  if (str && str.contains("color:red")) return (true);
  return (false);
}

// Conversions ----------------------------------------------------------------------------------------------------------------


// TODO get this info from the hub settings and cache it to avoid multiple calls to the parent gateway
// for every sensor device on every cycle (15-20 calls per cycle → 1 call on cache miss or invalidation)
private Boolean unitSystemIsMetric() {
  Boolean cached = metricCache.get(device.getId())
  if (cached != null) return cached
  Boolean val = parent.unitSystemIsMetric()
  metricCache.put(device.getId(), val)
  return val
}

void invalidateMetricCache() {
  metricCache.remove(device.getId())
}

// ------------------------------------------------------------

private String timeEpochToLocal(String time) {
  //
  // Convert Unix Epoch time (seconds) to local time with locale format
  //
  try {
    Long epoch = time.toLong() * 1000L;

    Date date = new Date(epoch);

    time = cachedDateFormat.format(date);
  }
  catch (Exception e) {
    logger('E', {"Exception in timeEpochToLocal(): ${e}"});
  }

  return (time);
}

// ------------------------------------------------------------

private BigDecimal convertRange(BigDecimal val, BigDecimal inMin, BigDecimal inMax, BigDecimal outMin, BigDecimal outMax, Boolean returnInt = true) {
  // Let make sure ranges are correct
  assert (inMin <= inMax);
  assert (outMin <= outMax);

  // Restrain input value
  if (val < inMin) val = inMin;
  else if (val > inMax) val = inMax;

  val = ((val - inMin) * (outMax - outMin)) / (inMax - inMin) + outMin;
  if (returnInt) {
    // If integer is required we use the Float round because the BigDecimal one is not supported/not working on Hubitat
    val = val.toFloat().round().toBigDecimal();
  }

  return (val);
}

// ------------------------------------------------------------

private BigDecimal convert_F_to_C(BigDecimal val) {
  return ((val - 32) / 1.8);
}

// ------------------------------------------------------------

private BigDecimal convert_C_to_F(BigDecimal val) {
  return ((val * 1.8) + 32);
}

// ------------------------------------------------------------

private BigDecimal convert_inHg_to_hPa(BigDecimal val) {
  return (val * 33.863886666667);
}

// ------------------------------------------------------------

private BigDecimal convert_hPa_to_inHg(BigDecimal val) {
  return (val / 33.863886666667);
}

// ------------------------------------------------------------

private BigDecimal convert_in_to_mm(BigDecimal val) {
  return (val * 25.4);
}

// ------------------------------------------------------------

private BigDecimal convert_ft_to_m(BigDecimal val) {
  return (val / 3.28084);
}

// ------------------------------------------------------------

private BigDecimal convert_mi_to_km(BigDecimal val) {
  return (val * 1.609344);
}

// ------------------------------------------------------------

private BigDecimal convert_km_to_mi(BigDecimal val) {
  return (val / 1.609344);
}

// ------------------------------------------------------------

private BigDecimal convert_Wm2_to_lux(BigDecimal val) {
  return (val / 0.0079);
}

// ------------------------------------------------------------

private BigDecimal convert_gm3_to_ozyd3(BigDecimal val) {
  return (val / 37.079776);
}

// ------------------------------------------------------------

def clearAllStates() {
  logger('I', "Clearing all device states");
  state.clear()
  invalidateCache()
  invalidateMetricCache()
  initSensorFlags()
  attributeEnumerate(false).each {
    device.deleteCurrentState(it);
  }
}

// Sensor flag helpers (in-memory replacement for state.sensor/sensorTemp/sensorRain/sensorWind) ----------------------------

private Map getSensorFlags() {
  return sensorTracker.computeIfAbsent(device.getId()) { new java.util.concurrent.ConcurrentHashMap<String, Integer>() }
}

private void initSensorFlags() {
  sensorTracker.remove(device.getId())
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

private BigDecimal cachedNumber(String attribute) {
  Map cache = getAttrCache()
  if (cache.containsKey(attribute)) return cache[attribute]
  return device.currentValue(attribute) as BigDecimal
}

// Shared helpers to reduce code duplication -----------------------------------------------------------------------------------

private void initSetting(String name, Object defaultValue, String type) {
  if (settings[name] == null) device.updateSetting(name, [value: defaultValue, type: type])
}

private Boolean settingEnabled(String name, Object defaultValue) {
  initSetting(name, defaultValue, "bool")
  return settings[name]
}

private Boolean updateDangerColor(String danger, String color, String attribDanger, String attribColor) {
  Boolean updated = attributeUpdateString(danger, attribDanger)
  if (attributeUpdateString(color, attribColor)) updated = true
  return updated
}

private void deleteStaleState(String attribute) {
  if (device.currentValue(attribute) != null) device.deleteCurrentState(attribute)
}

// Attribute handling ----------------------------------------------------------------------------------------------------------
// TODO duplicated code in ecowitt_gateway.groovy - refactor?
private Boolean attributeUpdateString(String val, String attribute) {
  //
  // Only update "attribute" if different
  // Return true if "attribute" has actually been updated/created
  //
  Map cache = getAttrCache()
  String cached = cachedString(attribute)
  if (cached != val) {
    logger('D', {"attributeUpdateString(${attribute} : ${val}) - current value: ${cached}"});
    sendEvent(name: attribute, value: val);
    cache[attribute] = val
    return (true);
  }

  return (false);
}

// ------------------------------------------------------------
// TODO duplicated code in ecowitt_gateway.groovy - refactor?
private Boolean attributeUpdateNumber(BigDecimal val, String attribute, String measure = null, Integer decimals = -1) {
  //
  // Only update "attribute" if different
  // Return true if "attribute" has actually been updated/created
  //

  // If rounding is required we use the Float one because the BigDecimal is not supported/not working on Hubitat
  if (decimals >= 0) val = val.toFloat().round(decimals).toBigDecimal();

  BigDecimal integer = val.toBigInteger();

  // We don't strip zeros on an integer otherwise it gets converted to scientific exponential notation
  val = (val == integer)? integer: val.stripTrailingZeros();

  // Use cache to avoid repeated device.currentValue() database reads
  Map cache = getAttrCache()
  BigDecimal cached = cachedNumber(attribute)
  if (cached != val) {
    logger('D', {"attributeUpdateNumber(${attribute} : ${val}) - current value: ${cached}"});
    if (measure) sendEvent(name: attribute, value: val, unit: measure);
    else sendEvent(name: attribute, value: val);
    cache[attribute] = val
    return (true);
  }

  return (false);
}

// ------------------------------------------------------------

private List<String> attributeEnumerate(Boolean existing = true) {
  //
  // Return a list of all available attributes
  // If "existing" == true return only those that have been already created (non-null ones)
  // Returned list can be empty but never return null
  //
  List<String> list = [];
  List<com.hubitat.hub.domain.Attribute> attrib = device.getSupportedAttributes();
  if (attrib) {
    attrib.each {
      if (existing == false || device.currentValue(it.name) != null) list.add(it.name);
    }
  }

  return (list);
}

// ------------------------------------------------------------

private void attributeDeleteStale() {
  if (!settings.calcDewPoint) {
    deleteStaleState("dewPoint");
    deleteStaleState("humidityAbs");
  }

  if (!settings.calcHeatIndex) {
    deleteStaleState("heatIndex");
    deleteStaleState("heatDanger");
    deleteStaleState("heatColor");
  }

  if (!settings.calcSimmerIndex) {
    deleteStaleState("simmerIndex");
    deleteStaleState("simmerDanger");
    deleteStaleState("simmerColor");
  }

  if (!settings.calcWindChill) {
    deleteStaleState("windChill");
    deleteStaleState("windDanger");
    deleteStaleState("windColor");
  }

  if (!settings.reportSoilAD) {
    deleteStaleState("soilAD");
  }

  if (!settings.htmlEnabled) {
    deleteStaleState("batteryIcon");
    deleteStaleState("batteryTempIcon");
    deleteStaleState("batteryRainIcon");
    deleteStaleState("batteryWindIcon");

    deleteStaleState("heatDanger");
    deleteStaleState("heatColor");

    deleteStaleState("simmerDanger");
    deleteStaleState("simmerColor");

    deleteStaleState("aqiDanger");
    deleteStaleState("aqiColor");

    deleteStaleState("aqiDanger_avg_24h");
    deleteStaleState("aqiColor_avg_24h");

    deleteStaleState("waterMsg");
    deleteStaleState("waterColor");
  
    deleteStaleState("ultravioletDanger");
    deleteStaleState("ultravioletColor");

    deleteStaleState("windDanger");
    deleteStaleState("windColor");        
  }
}

// ------------------------------------------------------------

private Boolean attributeUpdateBattery(String val, String attribBattery, String attribBatteryIcon, String attribBatteryOrg, Integer type) {
  //
  // Convert all different batteries returned values to a 0-100% range
  // Type: 1) voltage: range from 1.30V (empty) to 1.65V (full)
  //       2) pentastep: range from 0 (empty) to 5 (full)
  //       0) binary: 0 (full) or 1 (empty)
  //       3) voltage solar: range from 0.3V (empty) to 5.3V (full)
  //
  if (attribBattery == "batterySolar") {
    if (!settingEnabled("reportBatterySolar", true)) return (false);
  }
  BigDecimal original = val.toBigDecimal();
  BigDecimal percent;
  BigDecimal icon;
  String unitOrg;

  switch (type) {
  case 1:
    // Change range from voltage to (0% - 100%)
    BigDecimal vMin, vMax;

    if (!(settings.voltageMin) || !(settings.voltageMax)) {
      // First time: initialize and show the preference
      vMin = 1.3;
      vMax = 1.65;

      device.updateSetting("voltageMin", [value: vMin, type: "string"]);
      device.updateSetting("voltageMax", [value: vMax, type: "string"]);
    }
    else {
      vMin = (settings.voltageMin).toBigDecimal();
      vMax = (settings.voltageMax).toBigDecimal();
    }

    percent = convertRange(original, vMin, vMax, 0, 100);
    unitOrg = "V";
    break;

  case 2:
    // Change range from (0 - 5) to (0% - 100%)
    percent = convertRange(original, 0, 5, 0, 100);
    unitOrg = "level";
    break;
      
  case 3:
    // Solar - change range from voltage to (0% - 100%)
    BigDecimal vMin, vMax;

    vMin = 0.3;
    vMax = 5.3;

    percent = convertRange(original, vMin, vMax, 0, 100);
    unitOrg = "V";
    break;

  default:
    // Change range from (0  or 1) to (100% or 0%)
    percent = (original == 0)? 100: 0;
    unitOrg = "!bool";
  }

  if (percent < 10) icon = 0;
  else if (percent < 30) icon = 20;
  else if (percent < 50) icon = 40;
  else if (percent < 70) icon = 60;
  else if (percent < 90) icon = 80;
  else icon = 100;

  Boolean updated = false;

  updated = attributeUpdateNumber(original, attribBatteryOrg, unitOrg);

  if (type != 2 || original != 6) {
    // We are not on USB power
    if (attributeUpdateNumber(percent, attribBattery, "%", 0)) updated = true;
    if (settings.htmlEnabled && attributeUpdateNumber(icon, attribBatteryIcon, "%")) updated = true;
  }

  return (updated);
}

// -----------------------------

private Boolean attributeUpdateLowestBattery() {
  BigDecimal percent = 100;
  String org = "0";
  Integer type = 0;

  BigDecimal temp = cachedNumber("batteryTemp");
  BigDecimal rain = cachedNumber("batteryRain");
  BigDecimal wind = cachedNumber("batteryWind");

  if (temp != null) {
    percent = temp;
    org = cachedString("batteryTempOrg");
    type = 0;
  }

  if (rain != null && rain < percent) {
    percent = rain;
    org = cachedString("batteryRainOrg");
    type = 1;
  }

  if (wind != null && wind < percent) {
    percent = wind;
    org = cachedString("batteryWindOrg");
    type = 1;
  }

  return (attributeUpdateBattery(org, "battery", "batteryIcon", "batteryOrg", type));
}

// ------------------------------------------------------------

private Boolean attributeUpdateTemperature(String val, String attribTemperature) {

  BigDecimal degrees = val.toBigDecimal();
  String measure = "°F";

  // Get number of decimals (default = 1)
  Integer decimals = settings.decsTemperature;
  if (decimals == null) {
    // First time: initialize and show the preference
    decimals = 1;
    device.updateSetting("decsTemperature", [value: decimals, type: "number"]);
  }

  // Convert to metric if requested
  if (unitSystemIsMetric()) {
    degrees = convert_F_to_C(degrees);
    measure = "°C";
  }

  return (attributeUpdateNumber(degrees, attribTemperature, measure, decimals));
}

// ------------------------------------------------------------

private Boolean attributeUpdateHumidity(String val, String attribHumidity) {

  BigDecimal percent = val.toBigDecimal();

  return (attributeUpdateNumber(percent, attribHumidity, "%", 0));
}

// ------------------------------------------------------------

private Boolean attributeUpdateLeafWetness(String val, String attribLeafWetness) {

  BigDecimal percent = val.toBigDecimal();

  return (attributeUpdateNumber(percent, attribLeafWetness, "%", 0));
}

// ------------------------------------------------------------
// lgk new fx
private Boolean attributeUpdateSoilAD(String val, String attribsoilad) {
 
    BigDecimal mv = val.toBigDecimal();  
    
   return (attributeUpdateNumber(mv, attribsoilad, "mv", 0));
}

// ------------------------------------------------------------

private Boolean attributeUpdatePressure(String val, String attribPressure, String attribPressureAbs) {

  // Get unit system
  Boolean metric = unitSystemIsMetric();

  // Get number of decimals (default = 2)
  Integer decimals = settings.decsPressure;
  if (decimals == null) {
    // First time: initialize and show the preference
    decimals = 2;
    device.updateSetting("decsPressure", [value: decimals, type: "number"]);
  }

  // Get pressure in hectopascal
  BigDecimal absolute = convert_inHg_to_hPa(val.toBigDecimal());

  // Get altitude in meters
  val = settings.localAltitude;
  if (!val) {
    // First time: initialize and show the preference
    val = metric? "0 m": "0 ft";
    device.updateSetting("localAltitude", [value: val, type: "string"]);
  }

  BigDecimal altitude;
  try {
    String[] field = val.split();
    altitude = field[0].toBigDecimal();
    if (field.size() == 1) {
      // No unit found: let's use the parent setting
      if (!metric) altitude = convert_ft_to_m(altitude);
    }
    else {
      // Found a unit: convert accordingly
      if (field[1][0] == "f" || field[1][0] == "F") altitude = convert_ft_to_m(altitude);
    }
  }
  catch(Exception ignored) {
    altitude = 0;
  }

  // Get temperature in celsius
  BigDecimal temperature = cachedNumber("temperature");
  if (temperature == null) temperature = 18;
  else if (!metric) temperature = convert_F_to_C(temperature);

  // Correct pressure to sea level using this conversion formula: https://keisan.casio.com/exec/system/1224575267
  BigDecimal relative = absolute * Math.pow(1 - ((altitude * 0.0065) / (temperature + (altitude * 0.0065) + 273.15)), -5.257);

  // Convert to imperial if requested
  if (metric) val = "hPa";
  else {
    absolute = convert_hPa_to_inHg(absolute);
    relative = convert_hPa_to_inHg(relative);
    val = "inHg";
  }

  Boolean updated = attributeUpdateNumber(relative, attribPressure, val, decimals);
  if (attributeUpdateNumber(absolute, attribPressureAbs, val, decimals)) updated = true;

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateRain(String val, String attribRain, Boolean hour = false) {
  if (!settings.reportRainData) {
    // First time: initialize and show the preference
    initSetting("reportRainData", true, "bool");
  } else {

    BigDecimal amount = val.toBigDecimal();
    String measure = hour? "in/h": "in";

    // Convert to metric if requested
    if (unitSystemIsMetric()) {
        amount = convert_in_to_mm(amount);
        measure = hour? "mm/h": "mm";
    }
    return (attributeUpdateNumber(amount, attribRain, measure, 2));
  }
  return (false);
}

// ------------------------------------------------------------

private Boolean attributeUpdatePM(String val, String attribPm) {

  BigDecimal pm = val.toBigDecimal();

  return (attributeUpdateNumber(pm, attribPm, "µg/m³"));
}

// ------------------------------------------------------------

private Boolean attributeUpdateAQI(String val, Boolean pm25, String attribAqi, String attribAqiDanger, String attribAqiColor) {
  //
  // Conversions based on https://en.wikipedia.org/wiki/Air_quality_index
  //
  BigDecimal pm = val.toBigDecimal();

  BigDecimal aqi;

  if (pm25) {
    // PM2.5
    if      (pm <  12.1) aqi = convertRange(pm,   0.0,  12.0,   0,  50);
    else if (pm <  35.5) aqi = convertRange(pm,  12.1,  35.4,  51, 100);
    else if (pm <  55.5) aqi = convertRange(pm,  35.5,  55.4, 101, 150);
    else if (pm < 150.5) aqi = convertRange(pm,  55.5, 150.4, 151, 200);
    else if (pm < 250.5) aqi = convertRange(pm, 150.5, 250.4, 201, 300);
    else if (pm < 350.5) aqi = convertRange(pm, 250.5, 350.4, 301, 400);
    else                 aqi = convertRange(pm, 350.5, 500.4, 401, 500);
  }
  else {
    // PM10
    if      (pm <  55)   aqi = convertRange(pm,   0,    54,     0,  50);
    else if (pm < 155)   aqi = convertRange(pm,  55,   154,    51, 100);
    else if (pm < 255)   aqi = convertRange(pm, 155,   254,   101, 150);
    else if (pm < 355)   aqi = convertRange(pm, 255,   354,   151, 200);
    else if (pm < 425)   aqi = convertRange(pm, 355,   424,   201, 300);
    else if (pm < 505)   aqi = convertRange(pm, 425,   504,   301, 400);
    else                 aqi = convertRange(pm, 505,   604,   401, 500);

    // Choose the highest AQI between PM2.5 and PM10
    BigDecimal aqi25 = cachedNumber(attribAqi);
    if (aqi < aqi25) aqi = aqi25;
  }

  // lgk set airQualityIndex only if actual aqi not avg
  if (attribAqi == "aqi") attributeUpdateNumber(aqi, "airQualityIndex", "AQI");

  Boolean updated = attributeUpdateNumber(aqi, attribAqi, "AQI");

  if (settings.htmlEnabled) {
    String danger;
    String color;

    if      (aqi <  51) { danger = "Good";                           color = "3ea72d"; }
    else if (aqi < 101) { danger = "Moderate";                       color = "fff300"; }
    else if (aqi < 151) { danger = "Unhealthy for Sensitive Groups"; color = "f18b00"; }
    else if (aqi < 201) { danger = "Unhealthy";                      color = "e53210"; }
    else if (aqi < 301) { danger = "Very Unhealthy";                 color = "b567a4"; }
    else if (aqi < 401) { danger = "Hazardous";                      color = "7e0023"; }
    else {                danger = "Hazardous";                      color = "7e0023"; }

    if (updateDangerColor(danger, color, attribAqiDanger, attribAqiColor)) updated = true;
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateCO2(String val, String attribCo2) {

  BigDecimal co2 = val.toBigDecimal();

  return (attributeUpdateNumber(co2, attribCo2, "ppm"));
}

// ------------------------------------------------------------

private Boolean attributeUpdateLeak(String val, String attribWater, String attribWaterMsg, String attribWaterColor) {

  BigDecimal leak = (val.toBigDecimal())? 1: 0;

  Boolean updated = attributeUpdateString(leak? "wet": "dry", attribWater);

  if (settings.htmlEnabled) {
    String message, color;

    if (leak) {
      message = "Leak detected!";
      color = "ff0000";
    }
    else {
      message = "Dry";
      color = "ffffff";
    }

    if (attributeUpdateString(message, attribWaterMsg)) updated = true;
    if (attributeUpdateString(color, attribWaterColor)) updated = true;
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateLightningDistance(String val, String attrib) {

  if (!val) val = "0";

  BigDecimal distance = val.toBigDecimal();
  String measure = "km";

  // Convert to imperial if requested
  if (!unitSystemIsMetric()) {
    distance = convert_km_to_mi(distance);
    measure = "mi";
  }

  return (attributeUpdateNumber(distance, attrib, measure, 1));
}

// ------------------------------------------------------------

private Boolean attributeUpdateLightningCount(String val, String attrib) {

  if (!val) val = "0";

  return (attributeUpdateNumber(val.toBigDecimal(), attrib));
}

// ------------------------------------------------------------

private Boolean attributeUpdateLightningTime(String val, String attrib) {

  val = (!val || val == "0")? "n/a": timeEpochToLocal(val);

  return (attributeUpdateString(val, attrib));
}

// ------------------------------------------------------------

private Boolean attributeUpdateLightningEnergy(String val, String attrib) {

  if (!val) val = "0";

  return (attributeUpdateNumber(val.toBigDecimal(), attrib, "MJ/m", 1));
}

// ------------------------------------------------------------

private Boolean attributeUpdateUV(String val, String attribUvIndex, String attribUvDanger, String attribUvColor) {
  //
  // Conversions based on https://en.wikipedia.org/wiki/Ultraviolet_index
  //
  BigDecimal index = val.toBigDecimal();

  Boolean updated = attributeUpdateNumber(index, attribUvIndex, "uvi");

  if (settings.htmlEnabled) {
    String danger;
    String color;

    if (index < 3)       { danger = "Low";       color = "3ea72d"; }
    else if (index < 6)  { danger = "Medium";    color = "fff300"; }
    else if (index < 8)  { danger = "High";      color = "f18b00"; }
    else if (index < 11) { danger = "Very High"; color = "e53210"; }
    else                 { danger = "Extreme";   color = "b567a4"; }

    if (updateDangerColor(danger, color, attribUvDanger, attribUvColor)) updated = true;
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateLight(String val, String attribSolarRadiation, String attribIlluminance) {

  BigDecimal light = val.toBigDecimal();

  Boolean updated = attributeUpdateNumber(light, attribSolarRadiation, "W/m²");
  if (attributeUpdateNumber(convert_Wm2_to_lux(light), attribIlluminance, "lux", 0)) updated = true;

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateWindSpeed(String val, String attribWindSpeed) {

  BigDecimal speed = val.toBigDecimal();
  String measure = "mph";

  // Convert to metric if requested
  if (unitSystemIsMetric()) {
    speed = convert_mi_to_km(speed);
    measure = "km/h";
  }

  return (attributeUpdateNumber(speed, attribWindSpeed, measure, 1));
}

// ------------------------------------------------------------

private Boolean attributeUpdateWindDirection(String val, String attribWindDirection, String attribWindCompass) {

  // First time: initialize and show the preference
  initSetting("calcWindCompass", true, "bool");
  
  BigDecimal direction = val.toBigDecimal();
  Boolean updated = attributeUpdateNumber(direction, attribWindDirection, "°");
  
  if (settings.calcWindCompass) {
    // BigDecimal doesn't support modulo operation so we roll up our own
    direction = direction - (direction.divideToIntegralValue(360) * 360);

    String compass;

    if (direction >= 348.75 || direction < 11.25) compass = "N";
    else if (direction < 33.75)                   compass = "NNE";
    else if (direction < 56.25)                   compass = "NE";
    else if (direction < 78.75)                   compass = "ENE";
    else if (direction < 101.25)                  compass = "E";
    else if (direction < 123.75)                  compass = "ESE";
    else if (direction < 146.25)                  compass = "SE";
    else if (direction < 168.75)                  compass = "SSE";
    else if (direction < 191.25)                  compass = "S";
    else if (direction < 213.75)                  compass = "SSW";
    else if (direction < 236.25)                  compass = "SW";
    else if (direction < 258.75)                  compass = "WSW";
    else if (direction < 281.25)                  compass = "W";
    else if (direction < 303.75)                  compass = "WNW";
    else if (direction < 326.25)                  compass = "NW";
    else                                          compass = "NNW";

    if (attributeUpdateString(compass, attribWindCompass)) updated = true;
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateDewPoint(String val, String attribDewPoint, String attribHumidityAbs) {
  Boolean updated = false;

  if (!settings.calcDewPoint) {
    // First time: initialize and show the preference
    initSetting("calcDewPoint", false, "bool");
  }
  else {
    BigDecimal temperature = cachedNumber("temperature");
    if (temperature != null) {

      if (!unitSystemIsMetric()) {
        // Convert temperature to C
        temperature = convert_F_to_C(temperature);
      }
  
      // Calculate dewPoint based on https://web.archive.org/web/20150209041650/http://www.gorhamschaffler.com:80/humidity_formulas.htm
      double rH = val.toDouble();

      double tC = temperature.doubleValue();

      /*
      //The next step is to obtain the saturation vapor pressure(Es) using formula (5) as before when air temperature is known.
      double Es = 6.11 * Math.pow(10, 7.5*tC/(237.7+tC));

      //The next step is to use the saturation vapor pressure and the relative humidity to compute the actual vapor pressure(E) of the air. This can be done with the following formula.
      double E = (rH*Es) / 100;

      //RH=relative humidity of air expressed as a percent.(i.e. 80%)
      //Now you are ready to use the following formula to obtain the dewpoint temperature.
      // Note: ln( ) means to take the natural log of the variable in the parentheses
      BigDecimal degrees = (-430.22 + (237.7 * Math.log(E))) / (-Math.log(E) + 19.08);
      log.debug("rH = " + rH + ", tC = " + tC + ", Es = " + Es + ", E = " + E + ", degrees = " + degrees);

      */
        
      // Calculate saturation vapor pressure in millibars
      double e = (tC < 0) ?
        6.1115 * Math.exp((23.036 - (tC / 333.7)) * (tC / (279.82 + tC))) :
        6.1121 * Math.exp((18.678 - (tC / 234.4)) * (tC / (257.14 + tC)));

      // Calculate current vapor pressure in millibars
      e *= rH / 100;

      BigDecimal degrees = (-430.22 + 237.7 * Math.log(e)) / (-Math.log(e) + 19.08);


      // Calculate humidityAbs based on https://carnotcycle.wordpress.com/2012/08/04/how-to-convert-relative-humidity-to-absolute-humidity/
      BigDecimal volume = ((6.1121 * Math.exp((17.67 * tC) / (tC + 243.5)) * rH * 2.1674)) / (tC + 273.15);
      
      // convert back to Fahrenheit, the attribUpdateTemperature does the final conversion back to C
      degrees = convert_C_to_F(degrees);
      if (!unitSystemIsMetric()) { volume = convert_gm3_to_ozyd3(volume); }

      if (attributeUpdateTemperature(degrees.toString(), attribDewPoint)) updated = true;
      if (attributeUpdateNumber(volume, attribHumidityAbs, unitSystemIsMetric()? "g/m³": "oz/yd³", 2)) updated = true;
    }
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateHeatIndex(String val, String attribHeatIndex, String attribHeatDanger, String attribHeatColor) {
  Boolean updated = false;

  if (!settings.calcHeatIndex) {
    // First time: initialize and show the preference
    initSetting("calcHeatIndex", false, "bool");
  }
  else {
    BigDecimal temperature = cachedNumber("temperature");
    if (temperature != null) {

      if (unitSystemIsMetric()) {
        // Convert temperature back to F
        temperature = convert_C_to_F(temperature);
      }

      // Calculate heatIndex based on https://en.wikipedia.org/wiki/Heat_index
      BigDecimal degrees;

      if (temperature < 80) degrees = temperature;
      else {
        BigDecimal humidity = val.toBigDecimal();

        degrees = -42.379 +
                  ( 2.04901523 * temperature) +
                  (10.14333127 * humidity) -
                  ( 0.22475541 * (temperature * humidity)) -
                  ( 0.00683783 * (temperature ** 2)) -
                  ( 0.05481717 * (humidity ** 2)) +
                  ( 0.00122874 * ((temperature ** 2) * humidity)) +
                  ( 0.00085282 * (temperature * (humidity ** 2))) -
                  ( 0.00000199 * ((temperature ** 2) * (humidity ** 2)));
      }

      updated = attributeUpdateTemperature(degrees.toString(), attribHeatIndex);

      if (settings.htmlEnabled) {
        String danger;
        String color;

        if (temperature < 80)  {
          danger = "Safe";
          color = "ffffff";
        }
        else {
          if      (degrees < 80)  { danger = "Safe";            color = "ffffff"; }
          else if (degrees < 91)  { danger = "Caution";         color = "ffff66"; }
          else if (degrees < 104) { danger = "Extreme Caution"; color = "ffd700"; }
          else if (degrees < 126) { danger = "Danger";          color = "ff8c00"; }
          else                    { danger = "Extreme Danger";  color = "ff0000"; }
        }

        if (updateDangerColor(danger, color, attribHeatDanger, attribHeatColor)) updated = true;
      }
    }
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateSimmerIndex(String val, String attribSimmerIndex, String attribSimmerDanger, String attribSimmerColor) {
  Boolean updated = false;

  if (!settings.calcSimmerIndex) {
    // First time: initialize and show the preference
    initSetting("calcSimmerIndex", false, "bool");
  }
  else {
    BigDecimal temperature = cachedNumber("temperature");
    if (temperature != null) {

      if (unitSystemIsMetric()) {
        // Convert temperature back to F
        temperature = convert_C_to_F(temperature);
      }

      // Calculate heatIndex based on https://www.vcalc.com/wiki/rklarsen/Summer+Simmer+Index
      BigDecimal degrees;

      if (temperature < 70) degrees = temperature;
      else {
        BigDecimal humidity = val.toBigDecimal();

        degrees = 1.98 * (temperature - (0.55 - (0.0055 * humidity)) * (temperature - 58.0)) - 56.83;
      }

      updated = attributeUpdateTemperature(degrees.toString(), attribSimmerIndex);

      if (settings.htmlEnabled) {
        String danger;
        String color;       

        if (temperature < 70)  {
          danger = "Cool";
          color = "ffffff";
        }
        else {
          if      (degrees < 70)  { danger = "Cool";                          color = "ffffff"; }
          else if (degrees < 77)  { danger = "Slightly Cool";                 color = "0099ff"; }
          else if (degrees < 83)  { danger = "Comfortable";                   color = "2dca02"; }
          else if (degrees < 91)  { danger = "Slightly Warm";                 color = "9acd32"; }
          else if (degrees < 100) { danger = "Increased Discomfort";          color = "ffb233"; }
          else if (degrees < 112) { danger = "Caution Heat Exhaustion";       color = "ff6600"; }
          else if (degrees < 125) { danger = "Danger Heatstroke";             color = "ff3300"; }
          else if (degrees < 150) { danger = "Extreme Danger";                color = "ff0000"; }
          else                    { danger = "Circulatory Collapse Imminent"; color = "cc3300"; }
        }

        if (updateDangerColor(danger, color, attribSimmerDanger, attribSimmerColor)) updated = true;
      }
    }
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateWindChill(String val, String attribWindChill, String attribWindDanger, String attribWindColor) {
  Boolean updated = false;

  if (!settings.calcWindChill) {
    // First time: initialize and show the preference
    initSetting("calcWindChill", false, "bool");
  }
  else {
    BigDecimal temperature = cachedNumber("temperature");
    if (temperature != null) {

      if (unitSystemIsMetric()) {
        // Convert temperature back to F
        temperature = convert_C_to_F(temperature);
      }

      // Calculate windChill based on https://en.wikipedia.org/wiki/Wind_chill
      BigDecimal degrees;
      BigDecimal windSpeed = val.toBigDecimal();

      if (temperature > 50 || windSpeed < 3) degrees = temperature;
      else degrees = 35.74 + (0.6215 * temperature) - (35.75 * (windSpeed ** 0.16)) + ((0.4275 * temperature) * (windSpeed ** 0.16));

      updated = attributeUpdateTemperature(degrees.toString(), attribWindChill);

      if (settings.htmlEnabled) {
        String danger;
        String color;   

        if (temperature > 50 || windSpeed < 3) {
          danger = "Safe";
          color = "ffffff";
        }
        else {
          if      (degrees < -69) { danger = "Frostbite certain";  color = "2d2c52"; }
          else if (degrees < -19) { danger = "Frostbite likely";   color = "1f479f"; }
          else if (degrees < 1)   { danger = "Frostbite possible"; color = "0c6cb5"; }
          else if (degrees < 21)  { danger = "Very Unpleasant";    color = "2f9fda"; }
          else if (degrees < 41)  { danger = "Unpleasant";         color = "9dc8e6"; }
          else                    { danger = "Safe";               color = "ffffff"; }
        }

        if (updateDangerColor(danger, color, attribWindDanger, attribWindColor)) updated = true;
      }
    }
  }

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateFirmware(String val, String attribFirmware) {

  Boolean updated = attributeUpdateString(val, attribFirmware);

  return (updated);
}

// ------------------------------------------------------------

private Boolean attributeUpdateHtml(String templHtml, String attribHtml) {

  Boolean updated = false;

  if (settings.htmlEnabled) {
    String index;
    String val;

    for (Integer idx = 0; idx < 16; idx++) {
      index = idx? "${idx}": "";

      val = device.getDataValue("${templHtml}${index}");
      if (!val) break;

      val = val.replaceAll(RE_HTML_VAR) { java.util.ArrayList match -> cachedString(match[1].trim()) }
      if (attributeUpdateString(val, "${attribHtml}${index}")) updated = true;
    }
  }

  return (updated);
}

// ------------------------------------------------------------

Boolean attributeUpdate(String key, String val) {
  //
  // Dispatch attributes changes to hub
  //

  Boolean updated = false;
  Boolean bundled = (device.getDataValue("isBundled") == "true");
  Boolean orphaned = false;   

  switch (key) {

    case "wh26batt":
      if (bundled) {
        getSensorFlags()["sensorTemp"] = 1;
        updated = attributeUpdateBattery(val, "batteryTemp", "batteryTempIcon", "batteryTempOrg", 0);  // !boolean
      }
      else {
        getSensorFlags()["sensor"] = 1;
        updated = attributeUpdateBattery(val, "battery", "batteryIcon", "batteryOrg", 0);
      }
      break;

    case "wh40batt":
      if (bundled) {
        getSensorFlags()["sensorRain"] = 1;
        updated = attributeUpdateBattery(val, "batteryRain", "batteryRainIcon", "batteryRainOrg", 1);  // voltage
      }
      else {
        getSensorFlags()["sensor"] = 1;
        updated = attributeUpdateBattery(val, "battery", "batteryIcon", "batteryOrg", 1);
      }
      break;

    case "wh68batt":
    case "wh80batt":
    case "wh90batt":
      if (bundled) {
        getSensorFlags()["sensorWind"] = 1;
        updated = attributeUpdateBattery(val, "batteryWind", "batteryWindIcon", "batteryWindOrg", 1);  // voltage
      }
      else {
        getSensorFlags()["sensor"] = 1;
        updated = attributeUpdateBattery(val, "battery", "batteryIcon", "batteryOrg", 1);
      }
      break;
    
    case "pm10_co2":
      updated = attributeUpdatePM(val, "pm10");
      if (attributeUpdateAQI(val, false, "aqi", "aqiDanger", "aqiColor")) updated = true;
      break;

    case "pm10_24h_co2":
      updated = attributeUpdatePM(val, "pm10_avg_24h");
      if (attributeUpdateAQI(val, false, "aqi_avg_24h", "aqiDanger_avg_24h", "aqiColor_avg_24h")) updated = true;
      break;

    case "co2":
      updated = attributeUpdateCO2(val, "carbonDioxide");
      break;

    case "co2_24h":
      updated = attributeUpdateCO2(val, "carbonDioxide_avg_24h");
      break;
   
    case "wh25batt":
    case "wh65batt":
    case RE_S_BATT_1_8:
      getSensorFlags()["sensor"] = 1;
      updated = attributeUpdateBattery(val, "battery", "batteryIcon", "batteryOrg", 0);  // !boolean
      break; 
        
    case "ws80cap_volt":
    case "ws90cap_volt":
    case RE_S_WS90CAP_VOLT:
      getSensorFlags()["sensor"] = 1;
      updated = attributeUpdateBattery(val, "batterySolar", "batterySolarIcon", "batterySolarOrg", 3); 
      break;
    
    case "baromrelin":
    case RE_S_BAROMRELIN_WF:
      // we ignore this value as we do our own correction
      break;

    case "baromabsin":
    case RE_S_BAROMABSIN_WF:
      updated = attributeUpdatePressure(val, "pressure", "pressureAbs");
      break;

    case "rainratein":
    case "rrain_piezo":
    case RE_S_RAINRATE_WF:
        updated = attributeUpdateRain(val, "rainRate", true);
        break;

    case "srain_piezo":
    case RE_S_SRAIN_PIEZO:
      if (settings.reportRainData) {
        getSensorFlags()["sensor"] = 1
        if (val == "1")
            updated = attributeUpdateString("true","raining");
        else updated = attributeUpdateString("false","raining");    
      }
      break;

    case "eventrainin":
    case "erain_piezo":
    case RE_S_EVENTRAIN_WF:
      updated = attributeUpdateRain(val, "rainEvent");
      break;

    case "hourlyrainin":
    case "hrain_piezo":
    case RE_S_HOURLYRAIN_WF:
      updated = attributeUpdateRain(val, "rainHourly");
      break;

    case "dailyrainin":
    case "drain_piezo":
    case RE_S_DAILYRAIN_WF:
      updated = attributeUpdateRain(val, "rainDaily");
      break;

    case "weeklyrainin":
    case "wrain_piezo":
    case RE_S_WEEKLYRAIN_WF:
      updated = attributeUpdateRain(val, "rainWeekly");
      break;

    case "monthlyrainin":
    case "mrain_piezo":
    case RE_S_MONTHLYRAIN_WF:
      updated = attributeUpdateRain(val, "rainMonthly");
      break;

    case "yearlyrainin":
    case "yrain_piezo":
    case RE_S_YEARLYRAIN_WF:
      updated = attributeUpdateRain(val, "rainYearly");
      break;

    case "totalrainin":
    case "train_piezo":
    case RE_S_TOTALRAIN_WF:
      updated = attributeUpdateRain(val, "rainTotal");
      break;

    case "pm25_co2":
    case RE_S_PM25_CH:
      updated = attributeUpdatePM(val, "pm25");
      if (attributeUpdateAQI(val, true, "aqi", "aqiDanger", "aqiColor")) updated = true;
      break;

    case "pm25_24h_co2":
    case RE_S_PM25_AVG:
      updated = attributeUpdatePM(val, "pm25_avg_24h");
      if (attributeUpdateAQI(val, true, "aqi_avg_24h", "aqiDanger_avg_24h", "aqiColor_avg_24h")) updated = true;
      break;
    
    case "lightning":
    case RE_S_LIGHTNING_WF:
      updated = attributeUpdateLightningDistance(val, "lightningDistance");
      break;

    case "lightning_num":
    case RE_S_LIGHTNING_NUM_WF:
      updated = attributeUpdateLightningCount(val, "lightningCount");
      break;

    case "lightning_time":
    case RE_S_LIGHTNING_TIME_WF:
      updated = attributeUpdateLightningTime(val, "lightningTime");
      break;

    case "uv":
    case RE_S_UV_WF:
      updated = attributeUpdateUV(val, "ultravioletIndex", "ultravioletDanger", "ultravioletColor");
      break;

    case "solarradiation":
    case RE_S_SOLAR_WF:
      updated = attributeUpdateLight(val, "solarRadiation", "illuminance");
      break;
        
    case "ws80_ver":
    case "ws90_ver":
    case RE_S_WS90_VER:
      updated = attributeUpdateFirmware(val, "firmware");
      break;
        
    case "winddir":
    case RE_S_WINDDIR_WF:
      updated = attributeUpdateWindDirection(val, "windDirection", "windCompass");
      break;

    case "winddir_avg10m":
    case RE_S_WINDDIR_AVG_WF:
      updated = attributeUpdateWindDirection(val, "windDirection_avg_10m", "windCompass_avg_10m");
      break;

    case "windspeedmph":
    case RE_S_WINDSPEED_WF:
      updated = attributeUpdateWindSpeed(val, "windSpeed");
      if (attributeUpdateWindChill(val, "windChill", "windDanger", "windColor")) updated = true;
      break;

    case "windspdmph_avg10m":
    case RE_S_WINDSPD_AVG_WF:
      updated = attributeUpdateWindSpeed(val, "windSpeed_avg_10m");
      break;

    case "windgustmph":
    case RE_S_WINDGUST_WF:
      updated = attributeUpdateWindSpeed(val, "windGust");
      break;

    case "maxdailygust":
    case RE_S_MAXGUST_WF:
      updated = attributeUpdateWindSpeed(val, "windGustMaxDaily");
      break;

    case "vpd":
    case RE_S_VPD:
      initSetting("reportVPD", false, "bool");
      if (settings.reportVPD) {
        getSensorFlags()["sensor"] = 1
        BigDecimal vpd = val.toBigDecimal()
        if (unitSystemIsMetric()) {
          attributeUpdateNumber(vpd, "vpd", "kPa", 4)
        } else {
          attributeUpdateNumber(vpd * 0.2953, "vpd", "inHg", 4)
        }
      }
      break;
    
    case "wh57batt":
    case "co2_batt":
    case RE_S_PM25BATT:
    case RE_S_LEAKBATT:
      getSensorFlags()["sensor"] = 1;
      updated = attributeUpdateBattery(val, "battery", "batteryIcon", "batteryOrg", 2);  // 0 - 5
      break;

    case "humidityin":
    case "humidity":
    case "humi_co2":
    case RE_S_HUMIDITY_WF:
    case RE_S_HUMIDITY_1_8:
      updated = attributeUpdateHumidity(val, "humidity");
      if (attributeUpdateDewPoint(val, "dewPoint", "humidityAbs")) updated = true;
      if (attributeUpdateHeatIndex(val, "heatIndex", "heatDanger", "heatColor")) updated = true;
      if (attributeUpdateSimmerIndex(val, "simmerIndex", "simmerDanger", "simmerColor")) updated = true;
      break;

    case "tempinf":
      // We set this here because it's the integrated GW1000 sensor, which has no battery
      getSensorFlags()["sensor"] = 1;
    case "tempf":
    case "tf_co2":
    case RE_S_TEMPF_WF:
    case RE_S_TEMP_1_8:
    case RE_S_TF_CH:
      updated = attributeUpdateTemperature(val, "temperature");
      break;
    //
    // End Of Data: update orphaned status and html attributes
    //
    case "endofdata":
      Map flags = getSensorFlags()
      if (flags.containsKey("sensorTemp")) {
        if (flags["sensorTemp"] == 0) orphaned = true;
        attributeUpdateString(flags["sensorTemp"] ? "false": "true", "orphanedTemp");
        flags["sensorTemp"] = 0;
      }

      if (flags.containsKey("sensorRain")) {
        if (flags["sensorRain"] == 0) orphaned = true;
        attributeUpdateString(flags["sensorRain"] ? "false": "true", "orphanedRain");
        flags["sensorRain"] = 0;
      }

      if (flags.containsKey("sensorWind")) {
        if (flags["sensorWind"] == 0) orphaned = true;
      attributeUpdateString(flags["sensorWind"] ? "false": "true", "orphanedWind");
        flags["sensorWind"] = 0;
      }      

      if (flags.containsKey("sensor")) {
        if (flags["sensor"] == 0) orphaned = true;
      attributeUpdateString(flags["sensor"] ? "false": "true", "orphaned");
        flags["sensor"] = 0;      
      }

      if (orphaned) {
        // Sensor or part the PWS bundle is not receiving data
        if (!devStatusIsError()) devStatus("Orphaned", "orange");
      }
      else {
        // Sensor or all parts of the PWS bundle are receiving data      
        if (!devStatusIsError()) devStatus(); 

        // If we are a bundled PWS sensor, at the endofdata we update the "virtual" battery with the lowest of all the "physical" batteries
        if (bundled) updated = attributeUpdateLowestBattery();
      }

      // Update HTML templates if any
      if (attributeUpdateHtml("htmlTemplate", "html")) updated = true;
      break;
    
    case RE_S_LEAK_CH:
      updated = attributeUpdateLeak(val, "water", "waterMsg", "waterColor");
      break;
    
    case RE_S_SOILMOISTURE:
      updated = attributeUpdateHumidity(val, "humidity");
      break;  

    case RE_S_SOILAD:
      initSetting("reportSoilAD", false, "bool")
      if (settings.reportSoilAD) {
        updated = attributeUpdateSoilAD(val, "soilAD")
      }
      break;

    case RE_S_LEAFWETNESS:
      updated = attributeUpdateLeafWetness(val, "leafWetness");
      break; 

    case RE_S_LIGHTNING_ENERGY_WF:
      updated = attributeUpdateLightningEnergy(val, "lightningEnergy");
      break;
    
    case RE_S_BATT_WF:
    case RE_S_LEAF_BATT:
    case RE_S_SOILBATT:
    case RE_S_TF_BATT:
      getSensorFlags()["sensor"] = 1;
      updated = attributeUpdateBattery(val, "battery", "batteryIcon", "batteryOrg", 1);  // voltage
      break;

    default:
      logger('W', {"Unrecognized attribute: ${key} = ${val}"});
      break;
  }
  return (updated);
}

// HTML templates --------------------------------------------------------------------------------------------------------------

private Object htmlGetRepository() {
  //
  // Return an Object containing all the templates
  // or null if something went wrong
  //
  Object repository = null;

  try {
    String repositoryText = "https://${gitHubUser()}.github.io/ecowitt/html/ecowitt.json".toURL().getText();
    if (repositoryText) {
      // text -> json
      Object parser = new groovy.json.JsonSlurper();
      repository = parser.parseText(repositoryText);
    }
  }
  catch (Exception e) {
    logger('E', {"Exception in versionUpdate(): ${e}"});
  }

  return (repository);
}

// ------------------------------------------------------------

private Integer htmlCountAttributes(String htmlAttrib) {
  //
  // Return the number of html attributes the driver has
  //
  Integer count = 0;

  // Get a list of all attributes (present/null or not)
  List<String> attribDrv = attributeEnumerate(false);
  String attrib;

  for (Integer idx = 0; idx < 16; idx++) {
    attrib = idx? "${htmlAttrib}${idx}": htmlAttrib;

    if (attribDrv.contains(attrib) == false) break;
    count++;
  }

  return (count);
}

// ------------------------------------------------------------

private void htmlDeleteAttributes(String htmlAttrib, Integer count) {

  String attrib;

  for (Integer idx = 0; idx < count; idx++) {
    attrib = idx? "${htmlAttrib}${idx}": htmlAttrib;

    if (device.currentValue(attrib) != null) device.deleteCurrentState(attrib);
  }
}

// ------------------------------------------------------------

private Integer htmlValidateTemplate(String htmlTempl, String htmlAttrib, Integer count) {
  //
  // Return  <0) number of invalid attributes in "htmlTempl"
  //        >=0) number of valid attributes in "htmlTempl"
  // Template is valid only if return > 0
  //
  String pattern = /\$\{([^}]+)\}/;

  // Build a list of valid attributes names excluding the null ones and ourself (for obvious reasons)
  List<String> attribDrv = attributeEnumerate();
  String attrib;

  for (Integer idx = 0; idx < count; idx++) {
    attrib = idx? "${htmlAttrib}${idx}": htmlAttrib;

    attribDrv.remove(attrib);
  }

  // Go through all the ${attribute} expressions in the htmlTempl and collect both good and bad ones
  List<String> attribOk = [];
  List<String> attribErr = [];

  htmlTempl.findAll(~pattern) { java.util.ArrayList match ->
    attrib = match[1].trim();

    if (attribDrv.contains(attrib)) attribOk.add(attrib);
    else attribErr.add(attrib);
  }

  if (attribErr.size() != 0) return (-attribErr.size());
  return (attribOk.size());
}

// ------------------------------------------------------------

private List<String> htmlGetUserInput(String input, Integer count) {
  //
  // Return null if user input is null or empty
  // Return empty list if user input is invalid: template(s) not found, duplicates, too many, etc.
  // Otherwise return a list of (unvalidated) templates entered by the user
  //
  if (!input) return (null);

  List<String> templateList = [];

  if (input.find(/[<>{};:=\'\"#&\$]/)) {
    // If input has at least one typical html character, then it's a real template
    templateList.add(input);
  }
  else {
    // Input is an array of repository template IDs
    List<String> idList = input.tokenize(", ");
    if (idList) {
      // We found at least one template ID in the user input, make sure they are not too many
      Object repository = htmlGetRepository();
      if (repository) {
        Boolean metric = unitSystemIsMetric();

        for (Integer idx = 0; idx < idList.size(); idx++) {
          // Try first the normal templates
          input = repository.templates."${idList[idx]}";

          // If not found try the unit templates
          if (!input) input = metric? repository.templatesMetric."${idList[idx]}": repository.templatesImperial."${idList[idx]}";

          // If still not found, or already found, or exceeded number of templates, return error
          if (!input || templateList.contains(input) || templateList.size() == count) return ([]);

          // Good one, let's add it
          templateList.add(input);
        }
      }
    }
  }

  return (templateList);
}

// ------------------------------------------------------------

private String htmlUpdateUserInput(String input) {
  //
  // Return:
  //            null) html templates have been disabled
  //              "") user input is empty or valid
  //   "<error_msg>") user input is invalid
  //
  String htmlTemplate = "htmlTemplate";
  String htmlAttrib = "html";

  // Delete old data templates (if any) 
  for (Integer idx = 0; idx < 16; idx++) {
    device.removeDataValue(idx? "${htmlTemplate}${idx}": htmlTemplate);
  }

  // Get the maximum number of supported templates
  Integer count = htmlCountAttributes(htmlAttrib);
  if (!count) {
    // Return if we do not support HTML templates
    return (null);
  }

  // Cleanup previous states and data
  htmlDeleteAttributes(htmlAttrib, count);

  // If templates are disabled we just exit here
  if (!settings.htmlEnabled) {
    return (null);      
  }

  // Parse user input
  List<String> templateList = htmlGetUserInput(input, count);
  if (templateList == null) {
    // Templates are disabled/empty
    return ("");
  }

  if (templateList.size() == 0) {
    // Invalid user input
    return ("Invalid template(s) id, count or repetition");
  }

  for (Integer idx = 0; idx < templateList.size(); idx++) {
    // We have valid templates: let's validate them
    if (htmlValidateTemplate(templateList[idx], htmlAttrib, count) < 1) {
      // Invalid or no attribute in template
      return ("Invalid attribute or template for the current sensor");
    }
  }

  // Finally! We have a (1 <= number <= count) of valid templates: let's write them down
  for (Integer idx = 0; idx < templateList.size(); idx++) {
    device.updateDataValue(idx? "${htmlTemplate}${idx}": htmlTemplate, templateList[idx]);
  }

  return ("");
}

// Driver Commands ------------------------------------------------------------------------------------------------------------

void settingsResetConditional() {

  device.removeSetting("localAltitude");
  device.removeSetting("voltageMin");
  device.removeSetting("voltageMax");
  device.removeSetting("calcDewPoint");
  device.removeSetting("calcHeatIndex");
  device.removeSetting("calcSimmerIndex");
  device.removeSetting("calcWindChill");    
}

// Driver lifecycle -----------------------------------------------------------------------------------------------------------

void installed() {
  try {
    logger('D', {"addedSensor(${device.getDeviceNetworkId()})"});
  }
  catch (Exception e) {
   logger('E', {"Exception in installed(): ${e}"});
  }
}

// ------------------------------------------------------------

void updated() {
  // Cache the logging level before any logging calls
  logger('I', '[updated]')
  updateCachedLoggingLevel()
  try {
    // Clear previous states and attributes
    state.clear();
    initSensorFlags()
    invalidateCache()
    invalidateMetricCache()
    attributeDeleteStale();

    // Pre-process HTML templates (if any)
    String error = htmlUpdateUserInput(settings.htmlTemplate as String);
    if (error) devStatus(error, "red");
    else devStatus();
   }
  catch (Exception e) {
    logger('E', {"Exception in updated(): ${e}"});
  }
}

// ------------------------------------------------------------

void uninstalled() {
  try {
    // Notify the parent we are being deleted
    getParent().uninstalledChildDevice(device.getDeviceNetworkId());

    logger('D', {"deletedSensor(${device.getDeviceNetworkId()})"});
  }
  catch (Exception e) {
    logger('E', {"Exception in uninstalled(): ${e}"});
  }
}

// Recycle bin ----------------------------------------------------------------------------------------------------------------

/*

private Integer attributeDelete(String attrib = null) {
  //
  // Delete the specified attribute or all if !attrib
  // Return the number of deleted attributes
  //
  Integer deleted = 0;

  List<com.hubitat.hub.domain.Attribute> list = device.getSupportedAttributes();
  if (list) {
    list.each {
      if ((!attrib || attrib == it.name) && device.currentValue(it.name) != null) {
        device.deleteCurrentState(it.name);
        deleted++;
      }
    }
  }

  return (deleted);
}

*/

// EOF ------------------------------------------------------------------------------------------------------------------------
