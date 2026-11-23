package vn.farm.alert;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.farm.alert.dto.event.AlertCreatedPayload;
import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.entity.Alert;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.entity.AlertStatus;
import vn.farm.alert.mapper.AlertMapper;
import vn.farm.alert.publisher.AlertEventPublisher;
import vn.farm.alert.repository.AlertRepository;
import vn.farm.alert.service.AlertService;
import vn.farm.alert.validation.AlertValidator;
import vn.farm.core.BusinessException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AlertServiceTests {

    private final AlertRepository repository = mock(AlertRepository.class);
    private final AlertEventPublisher publisher = mock(AlertEventPublisher.class);
    private final AlertService service = new AlertService(
            repository, Mappers.getMapper(AlertMapper.class), new AlertValidator(), publisher);

    @Test
    void createsNewAlertAndPublishesOnlyAfterCommit() {
        CreateAlertRequest request = validRequest();
        UUID alertId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        request.setReadingId(readingId);
        when(repository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert saved = invocation.getArgument(0);
            saved.setId(alertId);
            return saved;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            var response = service.createAlert(request);

            assertEquals(alertId, response.getId());
            assertEquals(AlertStatus.NEW, response.getStatus());
            assertEquals(readingId, response.getReadingId());
            verifyNoInteractions(publisher);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(publisher).publishAlertCreated(argThat((AlertCreatedPayload payload) ->
                    alertId.equals(payload.alertId())
                            && readingId.equals(payload.readingId())
                            && "OUT_OF_RANGE".equals(payload.type())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void queriesAlertsByNodeAndStatus() {
        UUID nodeId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.findByNodeIdAndStatus(nodeId, AlertStatus.NEW)).thenReturn(List.of(
                Alert.builder().id(id).nodeId(nodeId).status(AlertStatus.NEW).build()));

        var results = service.getAlertsByNodeIdAndStatus(nodeId, AlertStatus.NEW);

        assertEquals(1, results.size());
        assertEquals(id, results.getFirst().getId());
        assertEquals(nodeId, results.getFirst().getNodeId());
    }

    @Test
    void missingAlertReportsDomainError() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.getAlertById(id));

        assertEquals("ALERT_NOT_FOUND", error.getCode());
    }

    @Test
    void onlyAllowsNewToAcknowledgedToResolved() {
        UUID id = UUID.randomUUID();
        var alert = Alert.builder().id(id).status(AlertStatus.NEW).build();
        when(repository.findById(id)).thenReturn(Optional.of(alert));
        when(repository.save(alert)).thenReturn(alert);

        var earlyResolve = assertThrows(BusinessException.class, () -> service.resolveAlert(id));
        assertEquals("INVALID_STATUS_TRANSITION", earlyResolve.getCode());
        assertEquals(AlertStatus.NEW, alert.getStatus());
        assertEquals(AlertStatus.ACKNOWLEDGED, service.acknowledgeAlert(id).getStatus());
        assertEquals(AlertStatus.RESOLVED, service.resolveAlert(id).getStatus());

        var reopened = assertThrows(BusinessException.class, () -> service.acknowledgeAlert(id));
        assertEquals("INVALID_STATUS_TRANSITION", reopened.getCode());
        assertEquals(AlertStatus.RESOLVED, alert.getStatus());
    }

    @Test
    void rejectsBlankMessageBeforeWriting() {
        CreateAlertRequest request = validRequest();
        request.setMessage(" ");

        var error = assertThrows(BusinessException.class, () -> service.createAlert(request));

        assertEquals("INVALID_MESSAGE", error.getCode());
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void rejectsDuplicateReadingBeforeWriting() {
        CreateAlertRequest request = validRequest();
        UUID readingId = UUID.randomUUID();
        request.setReadingId(readingId);
        when(repository.existsByReadingId(readingId)).thenReturn(true);

        var error = assertThrows(BusinessException.class, () -> service.createAlert(request));

        assertEquals("DUPLICATE_SENSOR_ALERT", error.getCode());
        verify(repository).existsByReadingId(readingId);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(publisher);
    }

    private static CreateAlertRequest validRequest() {
        var request = new CreateAlertRequest();
        request.setNodeId(UUID.randomUUID());
        request.setAlertType("OUT_OF_RANGE");
        request.setSeverity(AlertSeverity.HIGH);
        request.setMessage("Temperature too high");
        return request;
    }
}
