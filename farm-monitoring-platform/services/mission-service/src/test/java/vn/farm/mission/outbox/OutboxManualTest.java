package vn.farm.mission.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

@SpringBootTest(
        properties = "spring.task.scheduling.enabled=false"
)
class OutboxManualTest {

    @Autowired
    private MissionOutboxService outboxService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void createTestEvent() {
        UUID executionId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                outboxService.saveEvent(
                        "mission.ready",
                        Map.of(
                                "executionId", executionId.toString(),
                                "missionId", missionId.toString(),
                                "test", true
                        )
                )
        );

        System.out.println("Created test event: " + executionId);
    }
}