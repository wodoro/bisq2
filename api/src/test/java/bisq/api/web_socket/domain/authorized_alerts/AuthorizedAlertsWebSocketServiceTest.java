package bisq.api.web_socket.domain.authorized_alerts;

import bisq.api.dto.security.alert.AuthorizedAlertDataDto;
import bisq.api.web_socket.subscription.Subscriber;
import bisq.api.web_socket.subscription.SubscriberRepository;
import bisq.api.web_socket.subscription.SubscriptionRequest;
import bisq.api.web_socket.subscription.Topic;
import bisq.bonded_roles.release.AppType;
import bisq.bonded_roles.security_manager.alert.AlertNotificationsService;
import bisq.bonded_roles.security_manager.alert.AlertType;
import bisq.bonded_roles.security_manager.alert.AuthorizedAlertData;
import bisq.common.json.JsonMapperProvider;
import org.glassfish.grizzly.websockets.WebSocket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizedAlertsWebSocketServiceTest {

    @Test
    void getJsonPayloadUsesSubscriberAppTypeParameter() throws Exception {
        AlertNotificationsService alertNotificationsService = mock(AlertNotificationsService.class);
        AuthorizedAlertData desktopAlert = createAlert("desktop-alert", AppType.DESKTOP, 10L);
        when(alertNotificationsService.getUnconsumedAlertsByAppType(AppType.DESKTOP))
                .thenReturn(Stream.of(desktopAlert));

        AuthorizedAlertsWebSocketService service = new AuthorizedAlertsWebSocketService(new SubscriberRepository(), alertNotificationsService);
        Subscriber subscriber = new Subscriber(Topic.AUTHORIZED_ALERTS, Optional.of("desktop"), "sub-1", mock(WebSocket.class));

        String json = service.getJsonPayload(subscriber).orElseThrow();

        List<AuthorizedAlertDataDto> payload = JsonMapperProvider.get().readerForListOf(AuthorizedAlertDataDto.class).readValue(json);
        assertThat(payload).hasSize(1);
        assertThat(payload.getFirst().appType()).isEqualTo(AppType.DESKTOP);
        verify(alertNotificationsService).getUnconsumedAlertsByAppType(AppType.DESKTOP);
    }

    @Test
    void getJsonPayloadDefaultsToMobileClientWhenRequestParameterMissing() throws Exception {
        AlertNotificationsService alertNotificationsService = mock(AlertNotificationsService.class);
        AuthorizedAlertData mobileAlert = createAlert("mobile-alert", AppType.MOBILE_CLIENT, 20L);
        when(alertNotificationsService.getUnconsumedAlertsByAppType(AppType.MOBILE_CLIENT))
                .thenReturn(Stream.of(mobileAlert));

        AuthorizedAlertsWebSocketService service = new AuthorizedAlertsWebSocketService(new SubscriberRepository(), alertNotificationsService);
        SubscriptionRequest request = SubscriptionRequest.fromJson("{\"type\":\"SubscriptionRequest\",\"requestId\":\"r1\",\"topic\":\"AUTHORIZED_ALERTS\"}").orElseThrow();

        String json = service.getJsonPayload(request).orElseThrow();

        List<AuthorizedAlertDataDto> payload = JsonMapperProvider.get().readerForListOf(AuthorizedAlertDataDto.class).readValue(json);
        assertThat(payload).hasSize(1);
        assertThat(payload.getFirst().appType()).isEqualTo(AppType.MOBILE_CLIENT);
        verify(alertNotificationsService).getUnconsumedAlertsByAppType(AppType.MOBILE_CLIENT);
    }

    @Test
    void getJsonPayloadRejectsInvalidAppType() {
        AlertNotificationsService alertNotificationsService = mock(AlertNotificationsService.class);
        AuthorizedAlertsWebSocketService service = new AuthorizedAlertsWebSocketService(new SubscriberRepository(), alertNotificationsService);
        Subscriber subscriber = new Subscriber(Topic.AUTHORIZED_ALERTS, Optional.of("invalid"), "sub-1", mock(WebSocket.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.getJsonPayload(subscriber));

        assertThat(exception).hasMessage("Invalid appType: invalid");
    }

    private AuthorizedAlertData createAlert(String id, AppType appType, long date) {
        return new AuthorizedAlertData(
                id,
                System.currentTimeMillis() + date,
                AlertType.INFO,
                Optional.of("Headline " + id),
                Optional.of("Message " + id),
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                "0123456789abcdef0123456789abcdef01234567",
                false,
                Optional.empty(),
                appType
        );
    }
}