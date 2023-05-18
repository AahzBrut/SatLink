package org.example;


import lombok.extern.slf4j.Slf4j;
import org.example.loaders.ConfigLoader;
import org.example.loaders.SchedulesLoader;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Slf4j
public class Main {
    public static void main(String[] args) {
        final var config = ConfigLoader.loadConfig();
        log.info("Config: " + config);

        final var connectionSchedules = SchedulesLoader.loadConnectionSchedules(Path.of(config.connectionSchedulesPath));
        final var flybySchedules = SchedulesLoader.loadFlybySchedules(Path.of(config.flybySchedulesPath));

        final var results = new HashMap<String, Double>();
        for (final var entry : flybySchedules){
            if (!results.containsKey(entry.getSatelliteName())) results.put(entry.getSatelliteName(), 0.0);
            results.put(entry.getSatelliteName(), results.get(entry.getSatelliteName()) + entry.getDuration());
        }
        log.info("Number of satellites: " + results.keySet().size());
        for (final var entry : results.entrySet()) {
            log.info(entry.getKey() + " - " + entry.getValue());
        }

        final var stations = new LinkedHashSet<String>();
        final var satellites = new LinkedHashSet<String>();
        final var stationsIndex = new HashMap<String, Integer>();
        final var satellitesIndex = new HashMap<String, Integer>();
        var stationCounter = 0;
        var satelliteCounter = 0;
        LocalDateTime startInstant = null;
        for (final var entry : connectionSchedules) {
            if (!stations.contains(entry.getStationName())) {
                stations.add(entry.getStationName());
                stationsIndex.put(entry.getStationName(), stationCounter++);
            }
            if (!satellites.contains(entry.getSatelliteName())) {
                satellites.add(entry.getSatelliteName());
                satellitesIndex.put(entry.getSatelliteName(), satelliteCounter++);
            }
            if (startInstant == null || startInstant.isAfter(entry.getStartTime())) {
                startInstant = entry.getStartTime();
            }
        }
        startInstant = LocalDateTime.of(startInstant.getYear(), startInstant.getMonth(), startInstant.getDayOfMonth(), 0, 0);
        final var sessions = new int[connectionSchedules.size()][4];
        var rowCounter = 0;
        for (final var entry : connectionSchedules) {
            sessions[rowCounter][0] = stationsIndex.get(entry.getStationName());
            sessions[rowCounter][1] = satellitesIndex.get(entry.getSatelliteName());
            sessions[rowCounter][2] = (int) ChronoUnit.MILLIS.between(startInstant, entry.getStartTime());
            sessions[rowCounter][3] = (int) ChronoUnit.MILLIS.between(startInstant, entry.getStopTime());
            rowCounter++;
        }
        Arrays.sort(sessions, (row1, row2) -> {
            if (row1[2] == row2[2]) return Integer.compare(row2[3], row1[3]);
            if (row1[2] > row2[2]) return 1;
            return -1;
        });

        log.info("Connections schedules parsing complete.");
    }
}
