/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.api.web_socket.domain.security_alerts;

import bisq.api.dto.DtoMappings;
import bisq.api.dto.security.alert.SecurityAlertDto;
import bisq.api.web_socket.domain.SimpleObservableWebSocketService;
import bisq.api.web_socket.subscription.SubscriberRepository;
import bisq.bonded_roles.release.AppType;
import bisq.bonded_roles.security_manager.alert.AlertNotificationsService;
import bisq.bonded_roles.security_manager.alert.AuthorizedAlertData;
import bisq.common.observable.Pin;
import bisq.common.observable.collection.ObservableSet;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

import static bisq.api.web_socket.subscription.Topic.SECURITY_ALERTS;

@Slf4j
public class SecurityAlertsWebSocketService extends SimpleObservableWebSocketService<ObservableSet<AuthorizedAlertData>, List<SecurityAlertDto>> {
    private static final AppType DEFAULT_APP_TYPE = AppType.MOBILE_CLIENT;
    private static final Comparator<AuthorizedAlertData> ALERT_RELEVANCE_COMPARATOR =
            Comparator.comparing(AuthorizedAlertData::getAlertType)
                    .thenComparing(AuthorizedAlertData::getDate)
                    .reversed();

    private final AlertNotificationsService alertNotificationsService;

    public SecurityAlertsWebSocketService(SubscriberRepository subscriberRepository,
                                          AlertNotificationsService alertNotificationsService) {
        super(subscriberRepository, SECURITY_ALERTS);
        this.alertNotificationsService = alertNotificationsService;
    }

    @Override
    protected Pin setupObserver() {
        return getObservable().addObserver(this::onChange);
    }

    @Override
    protected List<SecurityAlertDto> toPayload(ObservableSet<AuthorizedAlertData> observable) {
        return alertNotificationsService.getUnconsumedAlertsByAppType(DEFAULT_APP_TYPE)
                .sorted(ALERT_RELEVANCE_COMPARATOR)
                .map(DtoMappings.SecurityAlertMapping::fromBisq2Model)
                .toList();
    }

    @Override
    protected ObservableSet<AuthorizedAlertData> getObservable() {
        return alertNotificationsService.getUnconsumedAlerts();
    }
}