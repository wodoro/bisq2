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

package bisq.application;

import bisq.application.migration.MigrationService;
import bisq.common.application.ApplicationVersion;
import bisq.common.application.DevMode;
import bisq.common.application.OptionUtils;
import bisq.common.application.Service;
import bisq.common.asset.FiatCurrencyRepository;
import bisq.common.file.FileUtils;
import bisq.common.locale.CountryRepository;
import bisq.common.locale.LanguageRepository;
import bisq.common.locale.LocaleRepository;
import bisq.common.logging.AsciiLogo;
import bisq.common.logging.LogSetup;
import bisq.common.observable.Observable;
import bisq.common.util.ExceptionUtil;
import bisq.i18n.Res;
import bisq.persistence.PersistenceService;
import com.typesafe.config.ConfigFactory;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public abstract class ApplicationService implements Service {
    private static String resolveAppName(String[] args, com.typesafe.config.Config config) {
        return OptionUtils.findOptionValue(args, "--app-name")
                .or(() -> {
                    Optional<String> value = OptionUtils.findOptionValue(args, "--appName");
                    if (value.isPresent()) {
                        System.out.println("Warning: Use `--app-name` instead of deprecated `--appName`");
                    }
                    return value;
                })
                .orElseGet(() -> {
                    if (config.hasPath("appName")) {
                        return config.getString("appName");
                    } else {
                        return "Bisq2";
                    }
                });
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    public static final class Config {
        private static Config from(com.typesafe.config.Config config, String[] args, Path userDataDir) {
            String appName = resolveAppName(args, config);
            Path appDataDir = OptionUtils.findOptionValue(args, "--data-dir")
                    .map(Path::of)
                    .orElse(userDataDir.resolve(appName));
            return new Config(appDataDir,
                    appName,
                    config.getBoolean("devMode"),
                    config.getLong("devModeReputationScore"),
                    config.getString("keyIds"),
                    config.getBoolean("ignoreSigningKeyInResourcesCheck"),
                    config.getBoolean("ignoreSignatureVerification"),
                    config.getInt("memoryReportIntervalSec"),
                    config.getBoolean("includeThreadListInMemoryReport"),
                    config.getBoolean("checkInstanceLock"));
        }

        private final Path baseDir;
        private final String appName;
        private final boolean devMode;
        private final long devModeReputationScore;
        private final List<String> keyIds;
        private final boolean ignoreSigningKeyInResourcesCheck;
        private final boolean ignoreSignatureVerification;
        private final int memoryReportIntervalSec;
        private final boolean includeThreadListInMemoryReport;
        private final boolean checkInstanceLock;

        public Config(Path baseDir,
                      String appName,
                      boolean devMode,
                      long devModeReputationScore,
                      String keyIds,
                      boolean ignoreSigningKeyInResourcesCheck,
                      boolean ignoreSignatureVerification,
                      int memoryReportIntervalSec,
                      boolean includeThreadListInMemoryReport,
                      boolean checkInstanceLock) {
            this.baseDir = baseDir;
            this.appName = appName;
            this.devMode = devMode;
            this.devModeReputationScore = devModeReputationScore;
            // We want to use the keyIds at the DesktopApplicationLauncher as a simple format. 
            // Using the typesafe format with indexes would require a more complicate parsing as we do not use 
            // typesafe at the DesktopApplicationLauncher class. Thus, we use a simple comma separated list instead and treat it as sting in typesafe.
            this.keyIds = List.of(keyIds.split(","));
            this.ignoreSigningKeyInResourcesCheck = ignoreSigningKeyInResourcesCheck;
            this.ignoreSignatureVerification = ignoreSignatureVerification;
            this.memoryReportIntervalSec = memoryReportIntervalSec;
            this.includeThreadListInMemoryReport = includeThreadListInMemoryReport;
            this.checkInstanceLock = checkInstanceLock;
        }
    }

    private final com.typesafe.config.Config typesafeAppConfig;
    @Getter
    protected final Config config;
    @Getter
    protected final PersistenceService persistenceService;
    private Optional<MigrationService> migrationService;
    private FileLock instanceLock;
    @Getter
    protected final Observable<State> state = new Observable<>(State.INITIALIZE_APP);
    private InstanceLockManager instanceLockManager;

    public ApplicationService(String configFileName, String[] args, Path userDataDir) {
        com.typesafe.config.Config defaultTypesafeConfig = ConfigFactory.load(configFileName);
        defaultTypesafeConfig.checkValid(ConfigFactory.defaultReference(), configFileName);

        String appName = resolveAppName(args, defaultTypesafeConfig.getConfig("application"));
        Path appDataDir = OptionUtils.findOptionValue(args, "--data-dir")
                .map(Path::of)
                .orElse(userDataDir.resolve(appName));
        // J8 compatible to avoid issues on mobile Samsung devices
        File customConfigFile = Paths.get(appDataDir.toString(), "bisq.conf").toFile();
        com.typesafe.config.Config typesafeConfig;
        boolean customConfigProvided = customConfigFile.exists();
        if (customConfigProvided) {
            try {
                typesafeConfig = ConfigFactory.parseFile(customConfigFile).withFallback(defaultTypesafeConfig);
            } catch (Exception e) {
                System.err.println("Error when reading custom config file " + ExceptionUtil.getRootCauseMessage(e));
                throw new RuntimeException(e);
            }
        } else {
            typesafeConfig = defaultTypesafeConfig;
        }

        typesafeAppConfig = typesafeConfig.getConfig("application");
        config = Config.from(typesafeAppConfig, args, userDataDir);

        Path dataDir = config.getBaseDir();
        try {
            FileUtils.makeDirs(dataDir.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LogSetup.setup(dataDir.resolve("bisq").toString());
        log.info(AsciiLogo.getAsciiLogo());
        log.info("Data directory: {}", config.getBaseDir());
        log.info("Version: v{} / Commit hash: {}", ApplicationVersion.getVersion().getVersionAsString(), ApplicationVersion.getBuildCommitShortHash());
        log.info("Tor Version: v{}", ApplicationVersion.getTorVersionString());

        if (customConfigProvided) {
            log.info("Using custom config file");
        }

        DevMode.setDevMode(config.isDevMode());
        if (config.isDevMode()) {
            DevMode.setDevModeReputationScore(config.getDevModeReputationScore());
        }

        if (config.isCheckInstanceLock()) {
            checkInstanceLock();
        }

        Locale locale = LocaleRepository.getDefaultLocale();
        CountryRepository.applyDefaultLocale(locale);
        LanguageRepository.setDefaultLanguage(locale.getLanguage());
        FiatCurrencyRepository.setLocale(locale);
        Res.setLanguage(LanguageRepository.getDefaultLanguage());
        ResolverConfig.config();

        String absoluteDataDirPath = dataDir.toAbsolutePath().toString();
        persistenceService = new PersistenceService(absoluteDataDirPath);
        migrationService = Optional.of(new MigrationService(dataDir));
    }

    private void checkInstanceLock() {
        // Create a quasi-unique port per data directory
        // Dynamic/private ports: 49152 – 65535
        int lowestPort = 49152;
        int highestPort = 65535;
        int port = lowestPort + Math.abs(config.getBaseDir().hashCode() % (highestPort - lowestPort));
        instanceLockManager = new InstanceLockManager();
        instanceLockManager.acquireLock(port);

        // We release the instance lock with a shutdown hook to ensure it gets release in all cases.
        // If we throw the NoFileLockException we are still in constructor call and the ApplicationService instance is
        // not created, thus shutdown would not be called. Therefor the shutdown hook is a more reliable solution.
        // Usually we try to avoid adding multiple shutdownHooks as the order of their execution is not
        // defined. In that case the order from other hooks has no impact.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Thread.currentThread().setName("InstanceLockManager.releaseLock");
            instanceLockManager.releaseLock();
        }));
    }

    public CompletableFuture<Void> pruneAllBackups() {
        return persistenceService.pruneAllBackups();
    }

    public CompletableFuture<Boolean> readAllPersisted() {
        return persistenceService.readAllPersisted();
    }

    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> completableFuture = migrationService.orElseThrow().initialize();
        migrationService = Optional.empty();
        return completableFuture;
    }

    public abstract CompletableFuture<Boolean> shutdown();

    protected com.typesafe.config.Config getConfig(String path) {
        return typesafeAppConfig.getConfig(path);
    }

    protected boolean hasConfig(String path) {
        return typesafeAppConfig.hasPath(path);
    }

    protected void setState(State newState) {
        checkArgument(state.get().ordinal() < newState.ordinal(),
                "New state %s must have a higher ordinal as the current state %s", newState, state.get());
        state.set(newState);
        log.info("New state {}", newState);
    }
}
