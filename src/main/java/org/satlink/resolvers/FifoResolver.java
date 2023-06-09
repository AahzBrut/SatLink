package org.satlink.resolvers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.satlink.data.Config;
import org.satlink.data.SatelliteParams;
import org.satlink.data.Schedule;
import org.satlink.data.SkipTypes;
import org.satlink.exceptions.ResultIntegrityException;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class FifoResolver {
    private final Schedule connectionSchedule;
    private final Schedule flybySchedule;
    private final SatelliteParams[] satelliteParams;
    private final Config config;

    @SuppressWarnings({"java:S135", "java:S3518", "java:S125"})
    public void calculate() {
        sortConnectionSchedule(connectionSchedule.getRecords());
        sortFlybySchedule();

        final var connections = quantizeConnections(connectionSchedule.getRecords(), config.timeStep);
        final var satelliteTransactions = initSatelliteTransactions();
        final var stationTransactions = initStationTransactions();
        final var skipStats = new ArrayList<int[]>();

        for (int[] connection : connections) {
            final var stationId = connection[0];
            final var satelliteId = connection[1];
            final var startTime = connection[2];
            final var endTime = connection[3];

            final var currentTimeForStation = getCurrentTimeForStation(stationTransactions[stationId], startTime);
            final var currentTimeForSatellite = getCurrentTimeForSatellite(satelliteTransactions[satelliteId], startTime);
            final var currentTime = Math.max(Math.max(currentTimeForSatellite, currentTimeForStation), startTime);
            if (endTime <= currentTime) {
                if (endTime <= currentTimeForStation) {
                    skipStats.add(new int[]{SkipTypes.STATION_BUSY.ordinal(), stationId, satelliteId, startTime, endTime});
                }
                if (endTime <= currentTimeForSatellite) {
                    skipStats.add(new int[]{SkipTypes.SATELLITE_BUSY.ordinal(), stationId, satelliteId, startTime, endTime});
                }
                continue;
            }

            final var usedMemory = calcMemoryUsage(satelliteTransactions[satelliteId], currentTime, satelliteParams[satelliteId].getTransmitRatio(), satelliteParams[satelliteId].getMaxTimeAmount()) * satelliteParams[satelliteId].getTransmitRatio();
            var maxUploadMemory = endTime - currentTime;
            if (maxUploadMemory > usedMemory) maxUploadMemory = usedMemory;
            if (maxUploadMemory <= 0) {
                skipStats.add(new int[]{SkipTypes.SATELLITE_MEMORY_EMPTY.ordinal(), stationId, satelliteId, startTime, endTime});
                continue;
            }

            maxUploadMemory += currentTime;

            addStationTransaction(stationTransactions[stationId], satelliteId, currentTime, maxUploadMemory);
            addSatelliteTransaction(satelliteTransactions[satelliteId], stationId, currentTime, maxUploadMemory);
        }

        saveResultsAndStats(skipStats, satelliteTransactions, stationTransactions);
    }

    private int[][] quantizeConnections(int[][] records, int timeStep) {
        final var result = new ArrayList<int[]>();

        for (final var entry : records) {
            final var stationId = entry[0];
            final var satelliteId = entry[1];
            final var startTime = entry[2];
            final var stopTime = entry[3];
            var duration = stopTime - startTime;
            if (duration < 2 * timeStep) {
                result.add(entry);
            } else {
                var currentStartTime = startTime;
                while (duration > 2 * timeStep) {
                    result.add(new int[]{stationId, satelliteId, currentStartTime, currentStartTime + timeStep - 1});
                    currentStartTime += timeStep;
                    duration -= timeStep;
                }
                result.add(new int[]{stationId, satelliteId, currentStartTime, stopTime});
            }
        }
        final var schedule = new int[result.size()][4];
        result.toArray(schedule);
        sortConnectionSchedule(schedule);

        return schedule;
    }

    private void saveResultsAndStats(ArrayList<int[]> skipStats, List<int[]>[] satelliteTransactions, List<int[]>[] stationTransactions) {
        final var stationsSatellitesSchedules = initStationSatelliteSchedules();
        final var satelliteShootingPeriods = initSatelliteTransactions();

        checkInputDoubles();

        checkStationsTransactions(stationTransactions, stationsSatellitesSchedules);
        checkStationsTransactionsContinuity(stationTransactions);
        checkStationsTransactionsContinuity(satelliteTransactions);
        checkSatelliteShootingTransactions(satelliteTransactions, satelliteShootingPeriods);
        checkSatelliteTransactions(satelliteTransactions, stationTransactions);

        saveStationStats(stationTransactions);

        saveStationsSchedules();
        saveShootingSchedules();
        saveStationsTransactions(stationTransactions);
        saveSatelliteTransactions(satelliteTransactions);
        saveSkipWindowStats(skipStats);
        saveStationDataAmountReceived(stationTransactions);

        saveResultSchedules(stationTransactions);
    }

    private void saveStationDataAmountReceived(List<int[]>[] stationTransactions) {
        final var outputFile = Paths
                .get(config.statisticsPath)
                .resolve("StationDataAmountReceived.csv")
                .toFile();

        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save stations received data amounts.", e);
            return;
        }

        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            printWriter.println("Station name, Received amount(MB)");
            for (int i = 0; i < stationTransactions.length; i++) {
                final var entries = stationTransactions[i];
                var amount = 0.0;
                for (final var entry : entries) {
                    amount += (entry[2] - entry[1]) * 0.001 * satelliteParams[entry[0]].getBandwidth();
                }
                printWriter.println(String.format("%s, %10.3f", connectionSchedule.getStationNames()[i], amount));
            }
        } catch (Exception e) {
            log.error("Failed to save stations received data amounts.");
        }

    }

    private void saveResultSchedules(List<int[]>[] stationsSchedule) {
        for (var stationId = 0; stationId < stationsSchedule.length; stationId++) {
            saveStationResult(stationsSchedule[stationId], connectionSchedule.getStationNames()[stationId]);
        }
    }

    private void saveStationResult(List<int[]> stationSchedule, String stationName) {
        final var outputFile = Paths
                .get(config.resultsPath)
                .resolve(stationName + "-Schedule.txt")
                .toFile();
        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save station schedule.");
            return;
        }

        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            printWriter.println(stationName);
            printWriter.println("-------------------------");
            printWriter.println("Start Time (UTCG) * Stop Time (UTCG) * Duration (sec) * Satname * Data (Mbytes)");
            final var initialTime = connectionSchedule.getStartInstant();
            for (final var scheduleEntry : stationSchedule) {
                final var startTime = initialTime.plus(scheduleEntry[1], ChronoUnit.MILLIS);
                final var stopTime = initialTime.plus(scheduleEntry[2], ChronoUnit.MILLIS);
                final var duration = (scheduleEntry[2] - scheduleEntry[1]) * 0.001;
                final var satName = connectionSchedule.getSatelliteNames()[scheduleEntry[0]];
                final var data = satelliteParams[scheduleEntry[0]].getBandwidth() * duration;

                printWriter.println(String.format("%30s%30s%30.3f%30s%30.3f",
                        config.mainDateTimeFormatter.format(startTime),
                        config.mainDateTimeFormatter.format(stopTime),
                        duration,
                        satName,
                        data
                ));
            }
        } catch (Exception e) {
            log.error("Failed to save station schedule.");
        }
    }

    private void saveStationStats(List<int[]>[] stationTransactions) {
        final var outputFile = Paths
                .get(config.statisticsPath)
                .resolve("StationStats.csv")
                .toFile();

        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save station stats.");
            return;
        }
        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            final var rxLimits = getStationsRxLimit();
            final var stationCount = stationTransactions.length;
            printWriter.println("StationId, ReceiveTime, TimeLimit, SatellitesNumber");
            for (var i = 0; i < stationCount; i++) {
                final var satelliteCount = new HashSet<Integer>();
                var sumTransactionTime = 0L;
                for (final var transaction : stationTransactions[i]) {
                    satelliteCount.add(transaction[0]);
                    sumTransactionTime += transaction[2] - transaction[1];
                }
                printWriter.println(String.format("%d, %d, %d, %d", i, sumTransactionTime, rxLimits[i], satelliteCount.size()));
            }
        } catch (Exception e) {
            log.error("Failed to save station stats.");
        }
    }

    @SuppressWarnings({"Duplicates", "java:S1192"})
    private void saveSkipWindowStats(ArrayList<int[]> skipStats) {
        final var outputFile = Paths
                .get(config.statisticsPath)
                .resolve("SkipWindowStats.csv")
                .toFile();

        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save window skip statistics.", e);
            return;
        }

        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            printWriter.println("SkipType, StationId, SatelliteId, StartTime(UTC), StopTime(UTC), Duration(ms)");
            final var initialTime = connectionSchedule.getStartInstant();
            final var formatter = config.statisticsDateTimeFormatter;
            for (final var entry : skipStats) {
                final var startTime = formatter.format(initialTime.plus(entry[3], ChronoUnit.MILLIS));
                final var stopTime = formatter.format(initialTime.plus(entry[4], ChronoUnit.MILLIS));
                printWriter.println(String.format("%s, %d, %d, %s, %s, %d", SkipTypes.values()[entry[0]], entry[1], entry[2], startTime, stopTime, entry[4] - entry[3]));
            }
        } catch (Exception e) {
            log.error("Failed to save window skip statistics.");
        }
    }

    private void checkInputDoubles() {
        var lastEntry = new int[]{0, 0, 0, 0};
        var schedules = connectionSchedule.getRecords().clone();
        Arrays.sort(schedules, Arrays::compare);
        for (final var entry : schedules) {
            if (Arrays.compare(lastEntry, entry) == 0) {
                throw new ResultIntegrityException("Found doubles in input schedule.");
            }
            lastEntry = entry;
        }
    }

    @SuppressWarnings({"Duplicates", "java:S1192", "java:S3776"})
    private void saveSatelliteTransactions(List<int[]>[] satelliteTransactions) {
        final var outputFile = Paths
                .get(config.statisticsPath)
                .resolve("SatelliteTransactions.csv")
                .toFile();

        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save satellite transactions.", e);
            return;
        }

        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            printWriter.println("StationId, SatelliteId, StartTime(UTC), StopTime(UTC), Duration(ms), MemoryOnStart(ms), MemoryOnStop(ms), SentAmount(ms), IdleTime(ms)");
            final var initialTime = connectionSchedule.getStartInstant();
            final var formatter = config.statisticsDateTimeFormatter;
            for (int satelliteId = 0; satelliteId < satelliteTransactions.length; satelliteId++) {
                final var entries = satelliteTransactions[satelliteId];
                var memoryOnStart = 0;
                var memoryOnStop = 0;
                var sentAmount = 0;
                for (final var entry : entries) {
                    var idleTime = 0;
                    memoryOnStart = memoryOnStop;
                    sentAmount = entry[0] >= 0 ? (entry[2] - entry[1]) / satelliteParams[satelliteId].getTransmitRatio() : 0;

                    memoryOnStop += entry[0] < 0 ? entry[2] - entry[1] : -sentAmount;
                    memoryOnStop = memoryOnStop == -1 ? 0 : memoryOnStop;
                    if (memoryOnStop > satelliteParams[satelliteId].getMaxTimeAmount()) {
                        idleTime = memoryOnStop - satelliteParams[satelliteId].getMaxTimeAmount();
                        memoryOnStop = satelliteParams[satelliteId].getMaxTimeAmount();
                    }
                    final var startTime = formatter.format(initialTime.plus(entry[1], ChronoUnit.MILLIS));
                    final var stopTime = formatter.format(initialTime.plus(entry[2], ChronoUnit.MILLIS));
                    printWriter.println(String.format("%d, %d, %s, %s, %d, %d, %d, %d, %d", entry[0], satelliteId, startTime, stopTime, entry[2] - entry[1], memoryOnStart, memoryOnStop, sentAmount, idleTime));
                }
            }
        } catch (Exception e) {
            log.error("Failed to save satellite transactions.");
        }
    }

    @SuppressWarnings({"Duplicates", "java:S1192"})
    private void saveStationsTransactions(List<int[]>[] stationTransactions) {
        final var outputFile = Paths
                .get(config.statisticsPath)
                .resolve("StationTransactions.csv")
                .toFile();

        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save stations transactions.", e);
            return;
        }

        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            printWriter.println("StationId, SatelliteId, StartTime(UTC), StopTime(UTC), Duration(ms)");
            final var initialTime = connectionSchedule.getStartInstant();
            final var formatter = config.statisticsDateTimeFormatter;
            for (int i = 0; i < stationTransactions.length; i++) {
                final var entries = stationTransactions[i];
                for (final var entry : entries) {
                    final var startTime = formatter.format(initialTime.plus(entry[1], ChronoUnit.MILLIS));
                    final var stopTime = formatter.format(initialTime.plus(entry[2], ChronoUnit.MILLIS));
                    printWriter.println(String.format("%d, %d, %s, %s, %d", i, entry[0], startTime, stopTime, entry[2] - entry[1]));
                }
            }
        } catch (Exception e) {
            log.error("Failed to save stations transactions.");
        }
    }

    private void saveShootingSchedules() {
        final var outputFile = Paths
                .get(config.statisticsPath)
                .resolve("ShootingSchedules.csv")
                .toFile();

        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save shooting schedules.", e);
            return;
        }

        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            printWriter.println("SatelliteId, StartTime(UTC), StopTime(UTC), Duration(ms)");
            final var initialTime = connectionSchedule.getStartInstant();
            final var formatter = config.statisticsDateTimeFormatter;
            for (final var entry : flybySchedule.getRecords()) {
                final var startTime = formatter.format(initialTime.plus(entry[1], ChronoUnit.MILLIS));
                final var stopTime = formatter.format(initialTime.plus(entry[2], ChronoUnit.MILLIS));
                printWriter.println(String.format("%d, %s, %s, %d", entry[0], startTime, stopTime, entry[2] - entry[1]));
            }
        } catch (Exception e) {
            log.error("Failed to save shooting schedules.", e);
        }
    }

    private void saveStationsSchedules() {
        final var outputFile = Paths
                .get(config.statisticsPath)
                .resolve("StationsSchedules.csv")
                .toFile();

        try {
            //noinspection ResultOfMethodCallIgnored
            outputFile.getParentFile().mkdirs();
        } catch (Exception e) {
            log.error("Failed to save stations schedules.", e);
            return;
        }

        try (final var fileWriter = new FileWriter(outputFile);
             final var printWriter = new PrintWriter(fileWriter)
        ) {
            printWriter.println("StationId, SatelliteId, StartTime(UTC), StopTime(UTC), Duration(ms)");
            final var initialTime = connectionSchedule.getStartInstant();
            final var formatter = config.statisticsDateTimeFormatter;
            for (final var entry : connectionSchedule.getRecords()) {
                final var startTime = formatter.format(initialTime.plus(entry[2], ChronoUnit.MILLIS));
                final var stopTime = formatter.format(initialTime.plus(entry[3], ChronoUnit.MILLIS));
                printWriter.println(String.format("%d, %d, %s, %s, %d", entry[0], entry[1], startTime, stopTime, entry[3] - entry[2]));
            }
        } catch (Exception e) {
            log.error("Failed to save stations schedules.", e);
        }
    }

    private long[] getStationsRxLimit() {
        final var result = new long[connectionSchedule.getStationNames().length];
        final var stationsSchedules = initStationsSchedules();
        int stationCount = stationsSchedules.length;
        for (int i = 0; i < stationCount; i++) {
            final var stationTransactions = stationsSchedules[i];
            var lastStart = 0;
            var lastStop = 0;
            for (final var transaction : stationTransactions) {
                final var startTime = transaction[1];
                final var stopTime = transaction[2];
                if (lastStart == 0) lastStart = startTime;
                if (lastStop == 0) lastStop = stopTime;
                if (startTime > lastStop) {
                    result[i] += lastStop - lastStart;
                    lastStart = startTime;
                }
                lastStop = Math.max(stopTime, lastStop);
            }
            result[i] += lastStop - lastStart;
        }
        return result;
    }

    @SuppressWarnings("All")
    private List<int[]>[] initStationsSchedules() {
        final var result = new ArrayList[connectionSchedule.getStationNames().length];
        final var allSchedules = connectionSchedule.getRecords();
        for (final var schedule : allSchedules) {
            if (result[schedule[0]] == null) result[schedule[0]] = new ArrayList<int[]>();
            result[schedule[0]].add(new int[]{schedule[1], schedule[2], schedule[3]});
        }
        return result;
    }

    @SuppressWarnings("java:S3776")
    private void checkSatelliteShootingTransactions(List<int[]>[] satellitesTransactions, List<int[]>[] satellitesShootingPeriods) {
        final var satellitesCount = satellitesTransactions.length;
        for (var satelliteId = 0; satelliteId < satellitesCount; satelliteId++) {
            var satelliteTransactions = satellitesTransactions[satelliteId];
            for (final var transaction : satelliteTransactions) {
                final var stationId = transaction[0];
                if (stationId >= 0) continue;
                final var startTime = transaction[1];
                final var stopTime = transaction[2];

                var scheduleFound = false;
                for (var schedule : satellitesShootingPeriods[satelliteId]) {
                    if (schedule[1] <= startTime && schedule[2] >= stopTime) {
                        scheduleFound = true;
                        break;
                    }
                }

                if (!scheduleFound) {
                    final var message = "Shooting mismatched schedule!\nSatellite: " + satelliteId;
                    log.error(message);
                    throw new ResultIntegrityException(message);
                }
            }
        }
    }

    private void checkStationsTransactionsContinuity(List<int[]>[] stationsTransactions) {
        for (final var stationTransactions : stationsTransactions) {
            var lastStopTime = 0;
            for (final var transaction : stationTransactions) {
                final var startTime = transaction[1];
                final var stopTime = transaction[2];
                if (stopTime < startTime || lastStopTime >= startTime) {
                    final var message = "Continuity check failed.";
                    log.error(message);
                    throw new ResultIntegrityException(message);
                }
                lastStopTime = stopTime;
            }
        }
    }

    private void checkStationsTransactions(List<int[]>[] stationsTransactions, List<int[]>[][] stationsSchedules) {
        var stationCounter = 0;

        for (final var stationTransactions : stationsTransactions) {
            for (final var transaction : stationTransactions) {
                final var satelliteId = transaction[0];
                final var startTime = transaction[1];
                final var stopTime = transaction[2];

                var scheduleFound = false;

                for (final var schedule : stationsSchedules[stationCounter][satelliteId]) {
                    if (schedule[0] <= startTime && schedule[1] >= stopTime) {
                        scheduleFound = true;
                        break;
                    }
                }
                if (!scheduleFound) {
                    final var message = "Transaction mismatched schedule!\nStation: " + stationCounter + ", Satellite: " + satelliteId;
                    log.error(message);
                    throw new ResultIntegrityException(message);
                }
            }
            stationCounter++;
        }
    }

    @SuppressWarnings("java:S3776")
    private void checkSatelliteTransactions(List<int[]>[] satelliteTransactions, List<int[]>[] stationTransactions) {
        final var satelliteCount = satelliteTransactions.length;
        for (var i = 0; i < satelliteCount; i++) {
            final var transactions = satelliteTransactions[i];
            for (final var transaction : transactions) {
                if (transaction[0] < 0) continue;
                final var stationTransactionList = stationTransactions[transaction[0]];
                var matched = false;
                for (final var stationTransaction : stationTransactionList) {
                    if (i == stationTransaction[0] && transaction[1] == stationTransaction[1] && transaction[2] == stationTransaction[2]) {
                        matched = true;
                        break;
                    }
                }
                if (!matched)
                    throw new ResultIntegrityException(String.format("StationId: %d, SatelliteId: %d, StartTime: %d, StopTime: %d", transaction[0], i, transaction[1], transaction[2]));
            }
        }
    }

    @SuppressWarnings({"java:S3776", "java:S135"})
    private void addSatelliteTransaction(List<int[]> satelliteTransactions, int stationId, int currentTime, int stopTime) {
        satelliteTransactions.removeIf(e -> e[1] >= currentTime && e[2] <= stopTime);
        var insertIndex = -1;
        var splitIndex = -1;
        var splitStation = 0;
        var splitStart = 0;
        var splitEnd = 0;
        for (var i = 0; i < satelliteTransactions.size(); i++) {
            final var transaction = satelliteTransactions.get(i);
            if (transaction[1] < currentTime && transaction[2] <= stopTime && transaction[2] >= currentTime) {
                transaction[2] = currentTime - 1;
                insertIndex = i + 1;
            }
            if (transaction[1] >= currentTime && transaction[1] <= stopTime && transaction[2] > stopTime) {
                transaction[1] = stopTime + 1;
            }
            if (transaction[1] < currentTime && transaction[2] > stopTime) {
                splitEnd = transaction[2];
                transaction[2] = currentTime - 1;
                insertIndex = i + 1;
                splitIndex = i + 2;
                splitStation = transaction[0];
                splitStart = stopTime + 1;
            }
            if (insertIndex == -1 && transaction[1] > stopTime) {
                insertIndex = i;
                break;
            }
            if (transaction[1] > stopTime) break;
        }

        if (insertIndex == -1) {
            satelliteTransactions.add(new int[]{stationId, currentTime, stopTime});
        } else {
            satelliteTransactions.add(insertIndex, new int[]{stationId, currentTime, stopTime});
        }

        if (splitIndex >= 0) satelliteTransactions.add(splitIndex, new int[]{splitStation, splitStart, splitEnd});
    }

    private void addStationTransaction(List<int[]> stationTransactions, int stationId, int currentTime, int stopTime) {
        stationTransactions.add(new int[]{stationId, currentTime, stopTime});
    }

    private int getCurrentTimeForSatellite(List<int[]> stationTransactions, int minTime) {
        for (var i = stationTransactions.size() - 1; i >= 0; i--) {
            final var currentTransaction = stationTransactions.get(i);
            if (currentTransaction[0] >= 0) return currentTransaction[2] + 1;
        }
        return minTime;
    }

    private int getCurrentTimeForStation(List<int[]> stationTransactions, int minTime) {
        if (stationTransactions.isEmpty()) return minTime;
        final var lastTransaction = stationTransactions.get(stationTransactions.size() - 1);
        return lastTransaction[2] + 1;
    }

    private int calcMemoryUsage(List<int[]> satelliteTransactions, int currentTime, int transmitRatio, int maxTimeAmount) {
        var result = 0;
        for (int[] transaction : satelliteTransactions) {
            if (transaction[1] >= currentTime) break;
            if (transaction[2] < currentTime) {
                if (transaction[0] < 0) {
                    result += transaction[2] - transaction[1];
                    result = Math.min(result, maxTimeAmount);
                } else {
                    result -= (transaction[2] - transaction[1]) / transmitRatio;
                }
            } else {
                if (transaction[0] < 0) {
                    result += currentTime - transaction[1];
                    result = Math.min(result, maxTimeAmount);
                } else {
                    result -= (currentTime - transaction[1]) / transmitRatio;
                }
            }
        }
        return result;
    }

    @SuppressWarnings("All")
    private List<int[]>[] initStationTransactions() {
        final var result = new ArrayList[connectionSchedule.getStationNames().length];
        for (var i = 0; i < result.length; i++) {
            result[i] = new ArrayList<int[]>();
        }
        return result;
    }

    @SuppressWarnings("All")
    private List<int[]>[] initSatelliteTransactions() {
        final var result = new ArrayList[flybySchedule.getSatelliteNames().length];
        final var shootings = flybySchedule.getRecords();

        for (int[] shooting : shootings) {
            if (result[shooting[0]] == null) result[shooting[0]] = new ArrayList<int[]>();
            result[shooting[0]].add(new int[]{-1, shooting[1], shooting[2]});
        }
        return result;
    }

    @SuppressWarnings("All")
    private List<int[]>[][] initStationSatelliteSchedules() {
        final var result = new ArrayList[connectionSchedule.getStationNames().length][connectionSchedule.getSatelliteNames().length];
        final var schedules = connectionSchedule.getRecords();

        for (int[] schedule : schedules) {
            final var stationId = schedule[0];
            final var satelliteId = schedule[1];
            final var startTime = schedule[2];
            final var stopTime = schedule[3];

            if (result[stationId][satelliteId] == null) result[stationId][satelliteId] = new ArrayList<int[]>();

            result[stationId][satelliteId].add(new int[]{startTime, stopTime});
        }
        return result;
    }

    private void sortConnectionSchedule(int[][] array) {
        Arrays.sort(array, (row1, row2) -> {
            if (row1[2] == row2[2]) return Integer.compare(row1[3], row2[3]);
            return Integer.compare(row1[2], row2[2]);
        });
    }

    private void sortFlybySchedule() {
        Arrays.sort(flybySchedule.getRecords(), (row1, row2) -> {
            if (row1[0] == row2[0]) return Integer.compare(row1[1], row2[1]);
            return Integer.compare(row1[0], row2[0]);
        });
    }
}
