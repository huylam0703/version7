package vn.farm.farmdevice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.farm.farmdevice.entity.SensorType;
import vn.farm.farmdevice.repository.SensorTypeRepository;

/**
 * Thêm 6 loại cảm biến mặc định nếu chưa có. Khoảng giá trị là GIÁ TRỊ ĐỀ XUẤT: workbook API ghi
 * "numeric physical ranges should be confirmed by the team". Tắt bằng farm.seed.sensor-types=false.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "farm.seed.sensor-types", havingValue = "true", matchIfMissing = true)
public class SensorTypeSeeder implements ApplicationRunner {
    private final SensorTypeRepository repo;

    @Override
    public void run(ApplicationArguments args) {
        seed("SOIL_MOISTURE", "Độ ẩm đất", "%", 0.0, 100.0, 1);
        seed("AIR_TEMP", "Nhiệt độ không khí", "°C", -40.0, 80.0, 1);
        seed("AIR_HUMIDITY", "Độ ẩm không khí", "%", 0.0, 100.0, 1);
        seed("LIGHT", "Cường độ ánh sáng", "lux", 0.0, 100000.0, 0);
        seed("PH", "Độ pH", "pH", 0.0, 14.0, 2);
        seed("WATER_LEVEL", "Mực nước", "cm", 0.0, 500.0, 1);
    }

    private void seed(String code, String name, String unit, double min, double max, int decimals) {
        if (repo.findByCode(code).isPresent()) return;
        SensorType t = new SensorType();
        t.setCode(code);
        t.setName(name);
        t.setUnit(unit);
        t.setMinValid(min);
        t.setMaxValid(max);
        t.setDecimals((short) decimals);
        repo.save(t);
    }
}
