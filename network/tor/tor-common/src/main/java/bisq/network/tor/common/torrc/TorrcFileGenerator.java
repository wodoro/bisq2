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

package bisq.network.tor.common.torrc;

import bisq.common.file.FileMutatorUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TorrcFileGenerator {
    private final Path torrcPath;
    private final Map<String, String> torrcConfigMap;
    private final List<Map.Entry<String, String>> torrcOverrides;
    private final Set<DirectoryAuthority> customDirectoryAuthorities;

    public TorrcFileGenerator(Path torrcPath,
                              Map<String, String> torrcConfigMap,
                              Set<DirectoryAuthority> customDirectoryAuthorities) {
        this(torrcPath, torrcConfigMap, Collections.emptyList(), customDirectoryAuthorities);
    }

    public TorrcFileGenerator(Path torrcPath,
                              Map<String, String> torrcConfigMap,
                              List<Map.Entry<String, String>> torrcOverrides,
                              Set<DirectoryAuthority> customDirectoryAuthorities) {
        this.torrcPath = torrcPath;
        this.torrcConfigMap = torrcConfigMap;
        this.torrcOverrides = torrcOverrides;
        this.customDirectoryAuthorities = customDirectoryAuthorities;
    }

    public void generate() {
        Set<String> overriddenKeys = torrcOverrides.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        StringBuilder torrcStringBuilder = new StringBuilder();
        torrcConfigMap.forEach((key, value) -> {
            if (!overriddenKeys.contains(key)) {
                torrcStringBuilder.append(key)
                        .append(" ")
                        .append(value)
                        .append("\n");
            }
        });

        torrcOverrides.forEach(entry ->
                torrcStringBuilder.append(entry.getKey())
                        .append(" ")
                        .append(entry.getValue())
                        .append("\n")
        );

        customDirectoryAuthorities.forEach(dirAuthority ->
                torrcStringBuilder.append("DirAuthority ").append(dirAuthority.getNickname())
                        .append(" orport=").append(dirAuthority.getOrPort())
                        .append(" v3ident=").append(dirAuthority.getV3Ident())
                        .append(" 127.0.0.1:").append(dirAuthority.getDirPort())
                        .append(" ").append(dirAuthority.getRelayFingerprint())
                        .append("\n"));


        try {
            FileMutatorUtils.writeToPath(torrcStringBuilder.toString(), torrcPath);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't create torrc file: " + torrcPath.toAbsolutePath());
        }
    }
}
