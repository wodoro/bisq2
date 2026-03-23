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

package bisq.api.rest_api.endpoints.security_alerts;

import bisq.api.dto.DtoMappings;
import bisq.api.dto.security.alert.SecurityAlertDto;
import bisq.api.rest_api.endpoints.RestApiBase;
import bisq.bonded_roles.security_manager.alert.AlertNotificationsService;
import bisq.bonded_roles.security_manager.alert.AuthorizedAlertData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Path("/security-alerts")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Security Alerts API", description = "API for retrieving and dismissing visible security alerts")
public class SecurityAlertsRestApi extends RestApiBase {
    private static final Comparator<AuthorizedAlertData> ALERT_RELEVANCE_COMPARATOR =
            Comparator.comparing(AuthorizedAlertData::getAlertType)
                    .thenComparing(AuthorizedAlertData::getDate)
                    .reversed();

    private final AlertNotificationsService alertNotificationsService;

    public SecurityAlertsRestApi(AlertNotificationsService alertNotificationsService) {
        this.alertNotificationsService = alertNotificationsService;
    }

    @GET
    @Operation(
            summary = "Get visible security alerts",
            description = "Returns the currently visible, undismissed security alerts in display order.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Security alerts retrieved successfully",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SecurityAlertDto.class)))),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response getSecurityAlerts() {
        try {
            return buildOkResponse(getSortedSecurityAlerts());
        } catch (Exception e) {
            log.error("Error retrieving security alerts", e);
            return buildErrorResponse("An unexpected error occurred: " + e.getMessage());
        }
    }

    @DELETE
    @Path("/{alertId}")
    @Operation(
            summary = "Dismiss a visible security alert",
            description = "Dismisses a currently visible security alert for the local client state.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Security alert dismissed successfully"),
                    @ApiResponse(responseCode = "404", description = "Security alert not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response dismissSecurityAlert(@PathParam("alertId") String alertId) {
        try {
            Optional<AuthorizedAlertData> authorizedAlertData = alertNotificationsService.getUnconsumedAlerts().stream()
                    .filter(alert -> alert.getId().equals(alertId))
                    .findFirst();
            if (authorizedAlertData.isEmpty()) {
                return buildNotFoundResponse("Security alert not found with ID: " + alertId);
            }

            alertNotificationsService.dismissAlert(authorizedAlertData.get());
            return buildNoContentResponse();
        } catch (Exception e) {
            log.error("Error dismissing security alert {}", alertId, e);
            return buildErrorResponse("An unexpected error occurred: " + e.getMessage());
        }
    }

    private List<SecurityAlertDto> getSortedSecurityAlerts() {
        return alertNotificationsService.getUnconsumedAlerts().stream()
                .sorted(ALERT_RELEVANCE_COMPARATOR)
                .map(DtoMappings.SecurityAlertMapping::fromBisq2Model)
                .toList();
    }
}