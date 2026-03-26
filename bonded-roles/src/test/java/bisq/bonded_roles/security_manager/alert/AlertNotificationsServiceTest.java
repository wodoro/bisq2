package bisq.bonded_roles.security_manager.alert;

import bisq.bonded_roles.release.AppType;
import bisq.common.observable.collection.ObservableSet;
import bisq.settings.SettingsService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertNotificationsServiceTest {

    @Test
    void initializeWithUnspecifiedAppTypeCollectsAlertsForAllAppTypes() {
        ObservableSet<AuthorizedAlertData> authorizedAlerts = new ObservableSet<>();
        ObservableSet<String> consumedAlertIds = new ObservableSet<>();
        SettingsService settingsService = mock(SettingsService.class);
        AlertService alertService = mock(AlertService.class);
        when(settingsService.getConsumedAlertIds()).thenReturn(consumedAlertIds);
        when(alertService.getAuthorizedAlertDataSet()).thenReturn(authorizedAlerts);

        AlertNotificationsService service = new AlertNotificationsService(settingsService, alertService, AppType.UNSPECIFIED);
        service.initialize().join();

        AuthorizedAlertData desktopAlert = createAlert("desktop-alert", AppType.DESKTOP, 1L);
        AuthorizedAlertData mobileAlert = createAlert("mobile-alert", AppType.MOBILE_CLIENT, 2L);

        authorizedAlerts.add(desktopAlert);
        authorizedAlerts.add(mobileAlert);

        assertThat(service.getUnconsumedAlerts()).containsExactlyInAnyOrder(desktopAlert, mobileAlert);
        assertThat(service.getUnconsumedAlertsByAppType(AppType.MOBILE_CLIENT)).containsExactly(mobileAlert);
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