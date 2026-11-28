package vn.farm.sensordata.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.farm.sensordata.application.SensorReadingService;
import vn.farm.sensordata.domain.ReadingValueKind;
import vn.farm.sensordata.domain.SensorReading;
import vn.farm.sensordata.persistence.SensorReadingRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SensorReadingApiTest {
    private static final String PATH = "/api/sensor-readings";
    private static final LocalDateTime MEASURED_AT = LocalDateTime.of(2026, 11, 23, 9, 30);
    private static final ObjectMapper JSON = new ObjectMapper();

    private SensorReadingRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(SensorReadingRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        var service = new SensorReadingService(repository, Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new SensorReadingController(service)).build();
    }

    @ParameterizedTest
    @CsvSource({
            "AIR_TEMP, °C, PHYSICAL, -40, VALID",
            "AIR_TEMP, °C, PHYSICAL, 85, VALID",
            "AIR_TEMP, °C, PHYSICAL, 85.01, INVALID",
            "AIR_HUMIDITY, %, PHYSICAL, 100, VALID",
            "AIR_HUMIDITY, %, PHYSICAL, 100.01, INVALID",
            "LIGHT, lux, CONVERTED, 100000, VALID",
            "LIGHT, lux, PHYSICAL, -1, INVALID",
            "SOIL_MOISTURE, %, CONVERTED, 65.5, VALID",
            "SOIL_MOISTURE, cm, CONVERTED, 65.5, INVALID",
            "WATER_LEVEL, cm, CONVERTED, 500, VALID",
            "WATER_LEVEL, cm, CONVERTED, 500.01, INVALID",
            "WATER_LEVEL, ADC_COUNT, CONVERTED, 50, INVALID",
            "UNKNOWN, lux, PHYSICAL, 50, INVALID"
    })
    void validatesPhysicalAndConvertedReadings(String code, String unit, String kind, double value,
                                              String expectedStatus) throws Exception {
        stubSave();

        send(payload("esp32-a", code, value).put("unit", unit).put("valueKind", kind))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.id").value(42));

        SensorReading saved = savedReading();
        assertThat(saved.getSensorType()).isEqualTo(code);
        assertThat(saved.getSensorValue()).isEqualTo(value);
        assertThat(saved.getUnit()).isEqualTo(unit);
        assertThat(saved.getValueKind()).isEqualTo(ReadingValueKind.valueOf(kind));
        assertThat(saved.getValidationStatus()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @CsvSource({
            "0, ADC_COUNT, 12, VALID",
            "65, ADC_COUNT, 12, VALID",
            "4095, ADC_COUNT, 12, VALID",
            "4096, ADC_COUNT, 12, INVALID",
            "4095, ADC_COUNT, 10, INVALID",
            "20.5, ADC_COUNT, 12, INVALID",
            "-1, ADC_COUNT, 12, INVALID",
            "65, %, 12, INVALID",
            "65, ADC_COUNT, 32, INVALID"
    })
    void validatesAndStoresRawCounts(double value, String unit, int resolution, String expectedStatus)
            throws Exception {
        stubSave();

        send(payload("esp32-b", "SOIL_MOISTURE", value).put("unit", unit)
                .put("valueKind", "ADC_RAW").put("adcResolutionBits", resolution))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus));

        SensorReading saved = savedReading();
        assertThat(saved.getSensorValue()).isEqualTo(value);
        assertThat(saved.getValueKind()).isEqualTo(ReadingValueKind.ADC_RAW);
        assertThat(saved.getUnit()).isEqualTo(unit);
        assertThat(saved.getAdcResolutionBits()).isEqualTo(resolution);
    }

    @ParameterizedTest
    @CsvSource({
            "' temperature ', 85, AIR_TEMP, VALID",
            "humidity, 50, AIR_HUMIDITY, VALID",
            "PH, 7, PH, VALID",
            "SOIL_MOISTURE, 65, SOIL_MOISTURE, INVALID",
            "AIR_TEMP, 20, AIR_TEMP, INVALID"
    })
    void handlesLegacyPayloads(String input, double value, String storedCode, String expectedStatus)
            throws Exception {
        stubSave();

        send(payload("esp32-a", input, value))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus));

        SensorReading saved = savedReading();
        assertThat(saved.getSensorType()).isEqualTo(storedCode);
        assertThat(saved.getUnit()).isNull();
        assertThat(saved.getValueKind()).isNull();
    }

    @Test
    void verifiesMetadataCombinations() throws Exception {
        stubSave();
        send(payload("esp32-a", "WATER_LEVEL", 32).put("unit", "ADC_COUNT").put("valueKind", "ADC_RAW"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVALID"));
        send(payload("esp32-a", "LIGHT", 32).put("unit", "lux"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVALID"));
        send(payload("esp32-b", "LIGHT", 32).put("valueKind", "CONVERTED"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVALID"));
        send(payload("esp32-b", "WATER_LEVEL", 32).put("unit", "cm")
                .put("valueKind", "CONVERTED").put("adcResolutionBits", 12))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVALID"));
    }

    @Test
    void rejectsMalformedInputWithoutQueryingTheDatabase() throws Exception {
        send(payload("", "LIGHT", 500)).andExpect(status().isBadRequest());
        send(payload("x".repeat(101), "LIGHT", 500)).andExpect(status().isBadRequest());
        send(payload("esp32-a", "x".repeat(51), 500)).andExpect(status().isBadRequest());
        send(payload("esp32-a", "LIGHT", 500).put("latitude", 91)).andExpect(status().isBadRequest());
        send(payload("esp32-a", "LIGHT", 500).put("sensorValue", "NaN")).andExpect(status().isBadRequest());
        send(payload("esp32-a", "LIGHT", 500).put("valueKind", 0)).andExpect(status().isBadRequest());
        send(payload("esp32-a", "WATER_LEVEL", 500).put("adcResolutionBits", 12.5))
                .andExpect(status().isBadRequest());
        send(payload("esp32-a", "WATER_LEVEL", 500).put("measuredAt", "9999-12-31T23:59:59.9995"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository);
    }

    @Test
    void checksAliasesAndRoundedTimestampWhileKeepingNodesIndependent() throws Exception {
        List<String> matchingTypes = List.of("AIR_TEMP", "TEMPERATURE");
        LocalDateTime normalized = MEASURED_AT.plusNanos(124_000_000);
        when(repository.existsReading("esp32-a", matchingTypes, normalized)).thenReturn(true);
        stubSave();

        send(airTemp("esp32-a").put("measuredAt", "2026-11-23T09:30:00.1235"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.id").doesNotExist());
        send(airTemp("esp32-b").put("measuredAt", "2026-11-23T09:30:00.1241"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("VALID"));

        verify(repository).existsReading("esp32-b", matchingTypes, normalized);
        SensorReading saved = savedReading();
        assertThat(saved.getNodeId()).isEqualTo("esp32-b");
        assertThat(saved.getMeasuredAt()).isEqualTo(normalized);
    }

    @ParameterizedTest
    @ValueSource(ints = {2601, 2627})
    void returnsDuplicateWhenSqlServerRejectsAConcurrentInsert(int sqlErrorCode) throws Exception {
        when(repository.saveAndFlush(any(SensorReading.class)))
                .thenThrow(integrityViolation(SensorReading.UNIQUE_CONSTRAINT, sqlErrorCode));

        send(airTemp("esp32-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DUPLICATE"));
    }

    @Test
    void propagatesUnrelatedIntegrityViolations() {
        DataIntegrityViolationException error = integrityViolation("other_constraint", 2627);
        when(repository.saveAndFlush(any(SensorReading.class))).thenThrow(error);

        assertThatThrownBy(() -> send(airTemp("esp32-a"))).hasRootCause(error.getMostSpecificCause());
    }

    @Test
    void historyReturnsExistingRowsAndNewMetadataInTheSameArray() throws Exception {
        var legacy = new SensorReading();
        legacy.setSensorType("TEMPERATURE");
        var raw = new SensorReading();
        raw.setSensorType("WATER_LEVEL");
        raw.setSensorValue(941d);
        raw.setValueKind(ReadingValueKind.ADC_RAW);
        raw.setUnit("ADC_COUNT");
        raw.setAdcResolutionBits(12);
        raw.setValidationStatus("INVALID");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(raw, legacy)));

        mvc.perform(get(PATH).param("limit", "501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sensorValue").value(941))
                .andExpect(jsonPath("$[0].unit").value("ADC_COUNT"))
                .andExpect(jsonPath("$[0].valueKind").value("ADC_RAW"))
                .andExpect(jsonPath("$[0].adcResolutionBits").value(12))
                .andExpect(jsonPath("$[0].validationStatus").value("INVALID"))
                .andExpect(jsonPath("$[1].sensorType").value("TEMPERATURE"))
                .andExpect(jsonPath("$[1].unit").isEmpty());

        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "measuredAt"));
    }

    private void stubSave() {
        when(repository.saveAndFlush(any(SensorReading.class))).thenAnswer(invocation -> {
            SensorReading reading = invocation.getArgument(0);
            ReflectionTestUtils.setField(reading, "id", 42L);
            return reading;
        });
    }

    private SensorReading savedReading() {
        var reading = ArgumentCaptor.forClass(SensorReading.class);
        verify(repository).saveAndFlush(reading.capture());
        return reading.getValue();
    }

    private ResultActions send(ObjectNode json) throws Exception {
        return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json.toString()));
    }

    private static ObjectNode airTemp(String nodeId) {
        return payload(nodeId, "AIR_TEMP", 30).put("unit", "°C").put("valueKind", "PHYSICAL");
    }

    private static ObjectNode payload(String nodeId, String code, double value) {
        return JSON.createObjectNode().put("nodeId", nodeId).put("sensorType", code).put("sensorValue", value)
                .put("measuredAt", MEASURED_AT.toString()).put("collectedAt", MEASURED_AT.plusSeconds(1).toString())
                .put("latitude", 10.03).put("longitude", 105.78);
    }

    private static DataIntegrityViolationException integrityViolation(String constraint, int sqlErrorCode) {
        var sqlError = new SQLException("Constraint violation", "23000", sqlErrorCode);
        var hibernateError = new ConstraintViolationException("Constraint violation", sqlError, constraint);
        return new DataIntegrityViolationException("Constraint violation", hibernateError);
    }
}
