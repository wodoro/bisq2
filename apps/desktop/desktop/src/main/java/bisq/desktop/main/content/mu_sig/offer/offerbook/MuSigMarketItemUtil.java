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

package bisq.desktop.main.content.mu_sig.offer.offerbook;

import bisq.common.market.Market;
import bisq.common.market.MarketRepository;

import java.util.Comparator;
import java.util.List;

public class MuSigMarketItemUtil {
    static final List<Market> majorMarkets = MarketRepository.getMajorFiatMarkets();
    
    static Comparator<MuSigMarketItem> sortByNumOffers() {
        return Comparator.<MuSigMarketItem>comparingLong(o -> o.getNumOffers().get()).reversed()
                .thenComparing(o -> o.getMarket().toString());
    }

    static Comparator<MuSigMarketItem> sortByMajorMarkets() {
        return (lhs, rhs) -> {
            int index1 = majorMarkets.indexOf(lhs.getMarket());
            if (index1 == -1) {
                index1 = Integer.MAX_VALUE;
            }
            int index2 = majorMarkets.indexOf(rhs.getMarket());
            if (index2 == -1) {
                index2 = Integer.MAX_VALUE;
            }
            return Integer.compare(index1, index2);
        };
    }

    static Comparator<MuSigMarketItem> sortByMarketNameAsc() {
        return Comparator.comparing(MuSigMarketItem::toString);
    }

    static Comparator<MuSigMarketItem> sortByMarketNameDesc() {
        return Comparator.comparing(MuSigMarketItem::toString).reversed();
    }

    static Comparator<MuSigMarketItem> sortByMarketActivity() {
        return (lhs, rhs) -> sortByNumOffers()
                .thenComparing(sortByMajorMarkets())
                .thenComparing(sortByMarketNameAsc())
                .compare(lhs, rhs);
    }
}
