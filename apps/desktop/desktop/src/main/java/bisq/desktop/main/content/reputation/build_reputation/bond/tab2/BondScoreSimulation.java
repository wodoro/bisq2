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

package bisq.desktop.main.content.reputation.build_reputation.bond.tab2;

import bisq.common.util.MathUtils;
import bisq.desktop.main.content.reputation.build_reputation.ScoreSimulation;
import bisq.presentation.parser.DoubleParser;
import bisq.user.reputation.BondedReputationService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

public class BondScoreSimulation extends ScoreSimulation {
    public BondScoreSimulation() {
        super();
    }

    @Override
    protected ScoreSimulation.Controller createController() {
        return new BondScoreSimulation.Controller();
    }

    @Slf4j
    public static class Controller extends ScoreSimulation.Controller {
        protected Controller() {
            super();
        }

        @Override
        public void onActivate() {
            super.onActivate();
        }

        @Override
        public void onDeactivate() {
            super.onDeactivate();
        }

        @Override
        protected void calculateSimScore() {
            try {
                // amountAsLong is the smallest unit of BSQ (100 = 1 BSQ)
                long amountAsLong = Math.max(0, MathUtils.roundDoubleToLong(DoubleParser.parse(model.getAmount().get()) * 100));
                long ageInDays = Math.max(0, model.getAge().get());
                long age = TimeUnit.DAYS.toMillis(ageInDays);
                long blockTime = System.currentTimeMillis() - age;
                long totalScore = BondedReputationService.doCalculateScore(amountAsLong, blockTime);
                String score = String.valueOf(totalScore);
                model.getScore().set(score);
            } catch (Exception e) {
                log.error("Failed to calculate simScore", e);
            }
        }
    }

    @Getter
    private static class Model extends ScoreSimulation.Model {
    }

    private static class View extends ScoreSimulation.View {
        private View(Model model, Controller controller) {
            super(model, controller);
        }
    }
}
