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

package bisq.common.network;

import java.nio.file.Path;

public interface TransportConfig {
    Path getDataDir();

    int getDefaultNodePort();

    int getSocketTimeout(); // in ms

    default int getDevModeDelayInMs() {
        return 0;
    }

    int getSendMessageThrottleTime(); // in ms

    int getReceiveMessageThrottleTime(); // in ms
}
