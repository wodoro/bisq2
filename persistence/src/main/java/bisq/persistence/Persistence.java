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

package bisq.persistence;

import bisq.common.threading.ExecutorFactory;
import bisq.common.util.StringUtils;
import bisq.persistence.backup.MaxBackupSize;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
public class Persistence<T extends PersistableStore<T>> {
    public static final String EXTENSION = ".protobuf";
    private static final ExecutorService EXECUTOR_SERVICE = ExecutorFactory.newSingleThreadExecutor("Persistence");

    @Getter
    private final Path storePath;
    @Getter
    private final String fileName;

    private final PersistableStoreReaderWriter<T> persistableStoreReaderWriter;

    public Persistence(String directory, String fileName, MaxBackupSize maxBackupSize) {
        this.fileName = fileName;
        String storageFileName = StringUtils.camelCaseToSnakeCase(fileName);
        storePath = Paths.get(directory, storageFileName + EXTENSION);
        var storeFileManager = new PersistableStoreFileManager(storePath, maxBackupSize);
        persistableStoreReaderWriter = new PersistableStoreReaderWriter<>(storeFileManager);
    }

    public CompletableFuture<Optional<T>> readAsync(Consumer<T> consumer) {
        return readAsync().whenComplete((result, throwable) -> result.ifPresent(consumer));
    }

    public CompletableFuture<Optional<T>> readAsync() {
        return CompletableFuture.supplyAsync(persistableStoreReaderWriter::read, EXECUTOR_SERVICE);
    }

    public CompletableFuture<Void> persistAsync(T serializable) {
        return CompletableFuture.runAsync(() -> persist(serializable), EXECUTOR_SERVICE);
    }

    protected void persist(T persistableStore) {
        persistableStoreReaderWriter.write(persistableStore);
    }

    public CompletableFuture<Void> pruneBackups() {
        return CompletableFuture.runAsync(persistableStoreReaderWriter::pruneBackups, EXECUTOR_SERVICE);
    }
}
