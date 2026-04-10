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

package bisq.network.tor.local_network;

import bisq.network.tor.common.torrc.TorrcFileGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TorrcFileGeneratorOverridesTest {

    @Test
    void overrideReplacesBaseConfigEntryWithSameKey(@TempDir Path tempDir) throws IOException {
        Path torrcPath = tempDir.resolve("torrc");
        Map<String, String> baseConfig = Map.of("SocksPort", "9050");

        List<Map.Entry<String, String>> overrides = List.of(
                new AbstractMap.SimpleImmutableEntry<>("SocksPort", "9999")
        );

        var generator = new TorrcFileGenerator(torrcPath, baseConfig, overrides, Set.of());
        generator.generate();

        String torrcContent = Files.readString(torrcPath);
        assertThat(torrcContent).contains("SocksPort 9999");
        long socksPortLineCount = torrcContent.lines()
                .filter(line -> line.startsWith("SocksPort "))
                .count();
        assertThat(socksPortLineCount).isEqualTo(1);
    }

    @Test
    void multipleOverrideEntriesWithSameKeyAreAllWritten(@TempDir Path tempDir) throws IOException {
        Path torrcPath = tempDir.resolve("torrc");
        Map<String, String> baseConfig = Map.of("SocksPort", "auto");

        List<Map.Entry<String, String>> overrides = List.of(
                new AbstractMap.SimpleImmutableEntry<>("Bridge", "obfs4 192.0.2.1:1234 FINGERPRINT1"),
                new AbstractMap.SimpleImmutableEntry<>("Bridge", "obfs4 192.0.2.2:5678 FINGERPRINT2")
        );

        var generator = new TorrcFileGenerator(torrcPath, baseConfig, overrides, Set.of());
        generator.generate();

        String torrcContent = Files.readString(torrcPath);
        assertThat(torrcContent)
                .contains("Bridge obfs4 192.0.2.1:1234 FINGERPRINT1")
                .contains("Bridge obfs4 192.0.2.2:5678 FINGERPRINT2");

        long bridgeLineCount = torrcContent.lines()
                .filter(line -> line.startsWith("Bridge "))
                .count();
        assertThat(bridgeLineCount).isEqualTo(2);
    }

    @Test
    void singleOverrideIsWritten(@TempDir Path tempDir) throws IOException {
        Path torrcPath = tempDir.resolve("torrc");
        Map<String, String> baseConfig = Map.of("SocksPort", "auto");

        List<Map.Entry<String, String>> overrides = List.of(
                new AbstractMap.SimpleImmutableEntry<>("UseBridges", "1")
        );

        var generator = new TorrcFileGenerator(torrcPath, baseConfig, overrides, Set.of());
        generator.generate();

        String torrcContent = Files.readString(torrcPath);
        assertThat(torrcContent).contains("UseBridges 1");
    }

    @Test
    void noOverridesDoesNotAddExtraLines(@TempDir Path tempDir) throws IOException {
        Path torrcPath = tempDir.resolve("torrc");
        Map<String, String> baseConfig = Map.of("SocksPort", "auto");

        var generator = new TorrcFileGenerator(torrcPath, baseConfig, List.of(), Set.of());
        generator.generate();

        String torrcContent = Files.readString(torrcPath);
        assertThat(torrcContent.lines()).hasSize(1);
        assertThat(torrcContent).contains("SocksPort auto");
    }

    @Test
    void baseConfigAndOverridesAreAllPresent(@TempDir Path tempDir) throws IOException {
        Path torrcPath = tempDir.resolve("torrc");
        Map<String, String> baseConfig = Map.of("UseBridges", "1");

        List<Map.Entry<String, String>> overrides = List.of(
                new AbstractMap.SimpleImmutableEntry<>("Bridge", "obfs4 192.0.2.1:1234 FP1"),
                new AbstractMap.SimpleImmutableEntry<>("Bridge", "obfs4 192.0.2.2:5678 FP2")
        );

        var generator = new TorrcFileGenerator(torrcPath, baseConfig, overrides, Set.of());
        generator.generate();

        String torrcContent = Files.readString(torrcPath);
        assertThat(torrcContent)
                .contains("UseBridges 1")
                .contains("Bridge obfs4 192.0.2.1:1234 FP1")
                .contains("Bridge obfs4 192.0.2.2:5678 FP2");
    }
}
