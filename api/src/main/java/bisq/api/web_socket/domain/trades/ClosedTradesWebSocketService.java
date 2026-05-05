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

package bisq.api.web_socket.domain.trades;

import bisq.api.dto.presentation.closed_trades.ClosedTradeIndexedItem;
import bisq.api.dto.presentation.closed_trades.ClosedTradeListItemDto;
import bisq.api.web_socket.domain.BaseWebSocketService;
import bisq.api.web_socket.domain.ClosedTradeItemsService;
import bisq.api.web_socket.subscription.ModificationType;
import bisq.api.web_socket.subscription.Subscriber;
import bisq.api.web_socket.subscription.SubscriberRepository;
import bisq.common.observable.Pin;
import bisq.common.observable.collection.CollectionObserver;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static bisq.api.web_socket.subscription.Topic.CLOSED_TRADES;

// Initial subscription payload is intentionally empty; clients paginate the closed-trade
// history via REST GET /trades/closed. The subscription delivers ADDED events for newly
// closed trades and REMOVED events if the domain removes a closed trade.
@Slf4j
public class ClosedTradesWebSocketService extends BaseWebSocketService {
    private final ClosedTradeItemsService closedTradeItemsService;
    @Nullable
    private Pin closedTradesPin;

    public ClosedTradesWebSocketService(SubscriberRepository subscriberRepository,
                                        ClosedTradeItemsService closedTradeItemsService) {
        super(subscriberRepository, CLOSED_TRADES);
        this.closedTradeItemsService = closedTradeItemsService;
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        closedTradesPin = closedTradeItemsService.getItems().addObserver(new CollectionObserver<>() {
            @Override
            public void onAdded(ClosedTradeIndexedItem item) {
                send(item.dto(), ModificationType.ADDED);
            }

            @Override
            public void onRemoved(Object element) {
                if (element instanceof ClosedTradeIndexedItem item) {
                    send(item.dto(), ModificationType.REMOVED);
                }
            }

            @Override
            public void onCleared() {
                send(Collections.emptyList(), ModificationType.REPLACE);
            }
        });
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> shutdown() {
        if (closedTradesPin != null) {
            closedTradesPin.unbind();
            closedTradesPin = null;
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Optional<String> getJsonPayload() {
        return toJson(Collections.emptyList());
    }

    private void send(ClosedTradeListItemDto item, ModificationType modificationType) {
        send(Collections.singletonList(item), modificationType);
    }

    private void send(List<ClosedTradeListItemDto> items, ModificationType modificationType) {
        List<Subscriber> subscribers = subscriberRepository.findSubscribers(topic).values().stream()
                .flatMap(Collection::stream)
                .toList();
        if (subscribers.isEmpty()) {
            return;
        }
        toJson(items).ifPresent(json ->
                subscribers.forEach(subscriber -> send(json, subscriber, modificationType)));
    }
}
