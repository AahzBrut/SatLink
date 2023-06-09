package org.satlink.loaders;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.satlink.data.*;
import org.satlink.exceptions.ConnectionSchedulesParserException;
import org.satlink.utils.FileUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@UtilityClass
public class SchedulesLoader {
    private static final String ERROR_TEXT = "Failed to parse file ";
    private static final String HEADER_MARKER = "-----";

    @SuppressWarnings("Duplicates")
    public static Schedule getConnectionSchedules(Path directoryPath, Config config) {
        final var schedules = loadConnectionSchedules(directoryPath, config);
        final var stations = new HashSet<String>();
        final var satellites = new HashSet<String>();
        final var stationsIndex = new HashMap<String, Integer>();
        final var satellitesIndex = new HashMap<String, Integer>();
        final var stationCounter = new AtomicInteger(0);
        final var satelliteCounter = new AtomicInteger(0);
        LocalDateTime startInstant = LocalDateTime.MAX;

        for (final var entry : schedules) {
            stations.add(entry.getStationName());
            satellites.add(entry.getSatelliteName());
            if (startInstant.isAfter(entry.getStartTime())) {
                startInstant = entry.getStartTime();
            }
        }

        startInstant = LocalDateTime.of(startInstant.getYear(), startInstant.getMonth(), startInstant.getDayOfMonth(), 0, 0);

        stations.stream().sorted().forEach(station -> stationsIndex.put(station, stationCounter.getAndIncrement()));
        final var stationNames = new String[stations.size()];
        stationsIndex.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(o -> stationNames[o.getValue()] = o.getKey());

        satellites.stream().sorted().forEach(satellite -> satellitesIndex.put(satellite, satelliteCounter.getAndIncrement()));
        final var satelliteNames = new String[satellites.size()];
        satellitesIndex.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(o -> satelliteNames[o.getValue()] = o.getKey());

        final var result = new int[schedules.size()][4];
        var rowCounter = 0;

        for (final var entry : schedules) {
            result[rowCounter][0] = stationsIndex.get(entry.getStationName());
            result[rowCounter][1] = satellitesIndex.get(entry.getSatelliteName());
            result[rowCounter][2] = (int) ChronoUnit.MILLIS.between(startInstant, entry.getStartTime());
            result[rowCounter][3] = (int) ChronoUnit.MILLIS.between(startInstant, entry.getStopTime());
            rowCounter++;
        }

        return new Schedule(
                startInstant,
                stationNames,
                satelliteNames,
                result
        );
    }

    @SuppressWarnings("Duplicates")
    public static Schedule getFlybySchedules(Path directoryPath, Config config) {
        final var schedules = loadFlybySchedules(directoryPath, config);
        final var satellites = new HashSet<String>();
        final var satellitesIndex = new HashMap<String, Integer>();
        final var satelliteCounter = new AtomicInteger(0);
        LocalDateTime startInstant = LocalDateTime.MAX;

        for (final var entry : schedules) {
            satellites.add(entry.getSatelliteName());
            if (startInstant.isAfter(entry.getStartTime())) {
                startInstant = entry.getStartTime();
            }
        }

        startInstant = LocalDateTime.of(startInstant.getYear(), startInstant.getMonth(), startInstant.getDayOfMonth(), 0, 0);

        satellites.stream().sorted().forEach(satellite -> satellitesIndex.put(satellite, satelliteCounter.getAndIncrement()));
        final var satelliteNames = new String[satellites.size()];
        satellitesIndex.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(o -> satelliteNames[o.getValue()] = o.getKey());

        final var result = new int[schedules.size()][3];
        var rowCounter = 0;

        for (final var entry : schedules) {
            result[rowCounter][0] = satellitesIndex.get(entry.getSatelliteName());
            result[rowCounter][1] = (int) ChronoUnit.MILLIS.between(startInstant, entry.getStartTime());
            result[rowCounter][2] = (int) ChronoUnit.MILLIS.between(startInstant, entry.getStopTime());
            rowCounter++;
        }

        return new Schedule(
                startInstant,
                null,
                satelliteNames,
                result
        );
    }

    public static List<SourceScheduleRecord> loadConnectionSchedules(Path directoryPath, Config config) {
        final var fileList = FileUtils.getFilteredFilesFromDirectory(config, directoryPath, SchedulesLoader::connectionScheduleFileFilter);
        final var result = new ArrayList<SourceScheduleRecord>();

        for (final var file : fileList) {
            result.addAll(parseConnectionScheduleFile(file, config));
        }
        return result;
    }

    public static List<FlybyScheduleRecord> loadFlybySchedules(Path directoryPath, Config config) {
        final var fileList = FileUtils.getFilteredFilesFromDirectory(config, directoryPath, SchedulesLoader::flybyScheduleFileFilter);
        final var result = new ArrayList<FlybyScheduleRecord>();

        for (final var file : fileList) {
            result.addAll(parseFlybyScheduleFile(file, config));
        }
        return result;
    }

    @SuppressWarnings("Duplicates")
    private static List<SourceScheduleRecord> parseConnectionScheduleFile(File file, Config config) {
        try {
            final var result = new ArrayList<SourceScheduleRecord>();
            final var lines = Files.readAllLines(file.toPath());
            String prevLine = null;
            var parserState = ParserStates.SEARCH_BLOCK_START;
            String stationName = null;
            String satelliteName = null;
            for (final var currentLine : lines) {
                switch (parserState) {
                    case SEARCH_BLOCK_START -> {
                        if (currentLine.startsWith(HEADER_MARKER) && prevLine != null && prevLine.contains("-To-")) {
                            final var headerParts = prevLine.trim().split("-");
                            stationName = headerParts[0];
                            satelliteName = headerParts[2];
                            parserState = parserState.nextState();
                        }
                    }
                    case SEARCH_DATA_START -> {
                        if (currentLine.trim().startsWith(HEADER_MARKER)) {
                            parserState = parserState.nextState();
                        }
                    }
                    case PARSING_DATA -> {
                        if (currentLine.isBlank()) {
                            parserState = parserState.nextState();
                            break;
                        }
                        final var access = Long.parseLong(currentLine.substring(0, 24).trim());
                        final var startTime = LocalDateTime.parse(currentLine.substring(28, 52).trim(), config.mainDateTimeFormatter);
                        final var stopTime = LocalDateTime.parse(currentLine.substring(56, 80).trim(), config.mainDateTimeFormatter);
                        final var duration = Double.parseDouble(currentLine.substring(85, 98).trim());
                        result.add(new SourceScheduleRecord(
                                stationName,
                                satelliteName,
                                access,
                                startTime,
                                stopTime,
                                duration
                        ));
                    }
                }
                prevLine = currentLine;
            }
            return result;
        } catch (Exception e) {
            log.error(ERROR_TEXT + file.getAbsolutePath(), e);
            throw new ConnectionSchedulesParserException(ERROR_TEXT + file.getAbsolutePath(), e);
        }
    }

    @SuppressWarnings("Duplicates")
    private static List<FlybyScheduleRecord> parseFlybyScheduleFile(File file, Config config) {
        try {
            final var result = new ArrayList<FlybyScheduleRecord>();
            final var lines = Files.readAllLines(file.toPath());
            String prevLine = null;
            var parserState = ParserStates.SEARCH_BLOCK_START;
            String satelliteName = null;
            for (final var currentLine : lines) {
                switch (parserState) {
                    case SEARCH_BLOCK_START -> {
                        if (currentLine.startsWith(HEADER_MARKER) && prevLine != null && prevLine.contains("-To-")) {
                            final var headerParts = prevLine.trim().split("-");
                            satelliteName = headerParts[2];
                            parserState = parserState.nextState();
                        }
                    }
                    case SEARCH_DATA_START -> {
                        if (currentLine.trim().startsWith(HEADER_MARKER)) {
                            parserState = parserState.nextState();
                        }
                    }
                    case PARSING_DATA -> {
                        if (currentLine.isBlank()) {
                            parserState = parserState.nextState();
                            break;
                        }
                        final var access = Long.parseLong(currentLine.substring(0, 24).trim());
                        final var startTime = LocalDateTime.parse(currentLine.substring(28, 52).trim(), config.mainDateTimeFormatter);
                        final var stopTime = LocalDateTime.parse(currentLine.substring(56, 80).trim(), config.mainDateTimeFormatter);
                        final var duration = Double.parseDouble(currentLine.substring(85, 98).trim());
                        result.add(new FlybyScheduleRecord(
                                satelliteName,
                                access,
                                startTime,
                                stopTime,
                                duration
                        ));
                    }
                }
                prevLine = currentLine;
            }
            return result;
        } catch (Exception e) {
            log.error(ERROR_TEXT + file.getAbsolutePath(), e);
            throw new ConnectionSchedulesParserException(ERROR_TEXT + file.getAbsolutePath(), e);
        }
    }

    private static boolean connectionScheduleFileFilter(File file, Config config) {
        return file.getName().startsWith(config.connectionScheduleFileNameStart);
    }

    private static boolean flybyScheduleFileFilter(File file, Config config) {
        return file.getName().startsWith(config.flybyScheduleFileNameStart);
    }

    public static SatelliteParams[] getSatellitesParams(String[] satelliteNames) {
        final var result = new SatelliteParams[satelliteNames.length];
        for (int i = 0; i < result.length; i++) {
            if (i < 50) {
                result[i] = new SatelliteParams(2500000, 4, 100);
            } else {
                result[i] = new SatelliteParams(1250000, 16, 25);
            }
        }

        return result;
    }
}
