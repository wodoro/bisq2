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

package bisq.network.tor;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TorTransportConfigTorrcOverridesTest {

    private static final String MINIMAL_CONFIG_TEMPLATE =
            "bootstrapTimeout=240\n" +
                    "hsUploadTimeout=120\n" +
                    "socketTimeout=120\n" +
                    "testNetwork=false\n" +
                    "directoryAuthorities=[]\n" +
                    "sendMessageThrottleTime=200\n" +
                    "receiveMessageThrottleTime=200\n" +
                    "useExternalTor=false\n";

    @Test
    void oldFormatSingleKeyValueIsSupported(@TempDir Path tempDir) {
        // Old format: torrcOverrides { SomeKey = someValue }
        // This is what all existing production configs use
        String configStr = MINIMAL_CONFIG_TEMPLATE +
                "torrcOverrides { SocksPort = 9999 }\n";

        var config = ConfigFactory.parseString(configStr);
        TorTransportConfig torTransportConfig = TorTransportConfig.from(tempDir, config);

        List<Map.Entry<String, String>> overrides = torTransportConfig.getTorrcOverrides();
        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).getKey()).isEqualTo("SocksPort");
        assertThat(overrides.get(0).getValue()).isEqualTo("9999");
    }

    @Test
    void newFormatListValueAllowsMultipleEntriesForSameKey(@TempDir Path tempDir) {
        // New format: torrcOverrides { Bridge = ["value1", "value2"] }
        // Needed for directives like Bridge that can legitimately appear multiple times
        String configStr = MINIMAL_CONFIG_TEMPLATE +
                "torrcOverrides {\n" +
                "  UseBridges = 1\n" +
                "  Bridge = [\"obfs4 192.0.2.1:1234 FP1\", \"obfs4 192.0.2.2:5678 FP2\"]\n" +
                "}\n";

        var config = ConfigFactory.parseString(configStr);
        TorTransportConfig torTransportConfig = TorTransportConfig.from(tempDir, config);

        List<Map.Entry<String, String>> overrides = torTransportConfig.getTorrcOverrides();
        long bridgeCount = overrides.stream().filter(e -> e.getKey().equals("Bridge")).count();
        assertThat(bridgeCount).isEqualTo(2);
        assertThat(overrides).anySatisfy(e -> {
            assertThat(e.getKey()).isEqualTo("Bridge");
            assertThat(e.getValue()).isEqualTo("obfs4 192.0.2.1:1234 FP1");
        });
        assertThat(overrides).anySatisfy(e -> {
            assertThat(e.getKey()).isEqualTo("Bridge");
            assertThat(e.getValue()).isEqualTo("obfs4 192.0.2.2:5678 FP2");
        });
        assertThat(overrides).anySatisfy(e -> {
            assertThat(e.getKey()).isEqualTo("UseBridges");
            assertThat(e.getValue()).isEqualTo("1");
        });
    }

    @Test
    void emptyOverridesProducesEmptyList(@TempDir Path tempDir) {
        // Production default: torrcOverrides={}
        String configStr = MINIMAL_CONFIG_TEMPLATE +
                "torrcOverrides={}\n";

        var config = ConfigFactory.parseString(configStr);
        TorTransportConfig torTransportConfig = TorTransportConfig.from(tempDir, config);

        assertThat(torTransportConfig.getTorrcOverrides()).isEmpty();
    }
}
