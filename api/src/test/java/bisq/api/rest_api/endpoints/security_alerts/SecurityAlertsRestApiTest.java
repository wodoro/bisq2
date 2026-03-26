package bisq.api.rest_api.endpoints.security_alerts;

import bisq.api.dto.security.alert.SecurityAlertDto;
import bisq.bonded_roles.release.AppType;
import bisq.bonded_roles.security_manager.alert.AlertNotificationsService;
import bisq.bonded_roles.security_manager.alert.AlertType;
import bisq.bonded_roles.security_manager.alert.AuthorizedAlertData;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAlertsRestApiTest {

    @Test
    void getSecurityAlertsDefaultsToMobileClient() {
        AlertNotificationsService alertNotificationsService = mock(AlertNotificationsService.class);
        AuthorizedAlertData mobileAlert = createAlert("mobile-alert", AppType.MOBILE_CLIENT, 10L);
        when(alertNotificationsService.getUnconsumedAlertsByAppType(AppType.MOBILE_CLIENT))
                .thenReturn(Stream.of(mobileAlert));

        SecurityAlertsRestApi restApi = new SecurityAlertsRestApi(alertNotificationsService);

        Response response = restApi.getSecurityAlerts(null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<SecurityAlertDto> securityAlerts = (List<SecurityAlertDto>) response.getEntity();
        assertThat(securityAlerts).hasSize(1);
        assertThat(securityAlerts.getFirst().appType()).isEqualTo(AppType.MOBILE_CLIENT);
        verify(alertNotificationsService).getUnconsumedAlertsByAppType(AppType.MOBILE_CLIENT);
    }

    @Test
    void dismissSecurityAlertUsesRequestedAppType() {
        AlertNotificationsService alertNotificationsService = mock(AlertNotificationsService.class);
        AuthorizedAlertData desktopAlert = createAlert("desktop-alert", AppType.DESKTOP, 20L);
        when(alertNotificationsService.getUnconsumedAlertsByAppType(AppType.DESKTOP))
                .thenReturn(Stream.of(desktopAlert));

        SecurityAlertsRestApi restApi = new SecurityAlertsRestApi(alertNotificationsService);

        Response response = restApi.dismissSecurityAlert("desktop-alert", "desktop");

        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        verify(alertNotificationsService).dismissAlert(desktopAlert);
    }

    @Test
    void getSecurityAlertsReturnsBadRequestForInvalidAppType() {
        AlertNotificationsService alertNotificationsService = mock(AlertNotificationsService.class);
        SecurityAlertsRestApi restApi = new SecurityAlertsRestApi(alertNotificationsService);

        Response response = restApi.getSecurityAlerts("invalid");

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "Invalid appType: invalid"));
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