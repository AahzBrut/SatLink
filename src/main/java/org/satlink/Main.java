package org.satlink;


import lombok.extern.slf4j.Slf4j;
import org.satlink.loaders.ConfigLoader;
import org.satlink.loaders.SchedulesLoader;
import org.satlink.resolvers.FifoResolver;

import java.nio.file.Path;

@Slf4j
public class Main {
    public static void main(String[] args) {
        final var config = ConfigLoader.loadConfig();
        log.info("Config loaded.");

        final var connectionSchedules = SchedulesLoader.getConnectionSchedules(Path.of(config.connectionSchedulesPath));
        final var flybySchedules = SchedulesLoader.getFlybySchedules(Path.of(config.flybySchedulesPath));
        final var satellitesParams = SchedulesLoader.getSatellitesParams(connectionSchedules.getSatelliteNames());

        log.info("Input schedules loaded.");

        final var resolver = new FifoResolver(connectionSchedules, flybySchedules, satellitesParams, config);

       resolver.calculate();

        log.info("Schedule calculation complete.");
    }
}
