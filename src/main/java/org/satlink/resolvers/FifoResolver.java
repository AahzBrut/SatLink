package org.satlink.resolvers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.satlink.data.Schedule;
import org.satlink.exceptions.ResultIntegrityException;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class FifoResolver {
    private final Schedule connectionSchedule;
    private final Schedule flybySchedule;

    @SuppressWarnings({"java:S135", "java:S3518", "java:S125"})
    public Schedule calculate() {
        sortConnectionSchedule();
        sortFlybySchedule();

        final var connections = connectionSchedule.getRecords();
        final var satelliteTransactions = initSatelliteTransactions();
        final var stationTransactions = initStationTransactions();
        //final var stationsSniffSchedules = initStationsSchedules();

        for (int[] connection : connections) {
            final var stationId = connection[0];
            final var satelliteId = connection[1];
            final var startTime = connection[2];
            final var endTime = connection[3];

            final var currentTimeForStation = getCurrentTimeForStation(stationTransactions[stationId], startTime);
            final var currentTimeForSatellite = getCurrentTimeForSatellite(satelliteTransactions[satelliteId], startTime);
            final var currentTime = Math.max(Math.max(currentTimeForSatellite, currentTimeForStation), startTime);
            if (endTime <= currentTime) continue;

            final var usedMemory = calcMemoryUsage(satelliteTransactions[satelliteId], currentTime) << 2;
            var maxUploadMemory = endTime - currentTime;
            if (maxUploadMemory > usedMemory) maxUploadMemory = usedMemory;
            if (maxUploadMemory <= 0) continue;

            maxUploadMemory += currentTime;
            //final var others = calculateOtherVariants(stationsSniffSchedules[stationId], satelliteId, Math.max(startTime, currentTimeForStation), maxUploadMemory);

            addStationTransaction(stationTransactions[stationId], satelliteId, currentTime, maxUploadMemory);
            addSatelliteTransaction(satelliteTransactions[satelliteId], stationId, currentTime, maxUploadMemory);
        }

        final var stationsSatellitesSchedules = initStationSatelliteSchedules();
        final var satelliteShootingPeriods = initSatelliteTransactions();
        checkStationsTransactions(stationTransactions, stationsSatellitesSchedules);
        checkStationsTransactionsContinuity(stationTransactions);
        checkStationsTransactionsContinuity(satelliteTransactions);
        checkSatelliteShootingTransactions(satelliteTransactions, satelliteShootingPeriods);

        var counter = 0;
        var avgMemUsage = 0.0;
        var totalSent = 0.0;
        for (final var schedule : satelliteTransactions) {
            final var memUsage = calcMemoryUsage(schedule, Integer.MAX_VALUE) / 25000.0;
            final var dataSent = calcSentVolume(schedule, Integer.MAX_VALUE);
            avgMemUsage += memUsage;
            totalSent += dataSent;
            log.info("Memory usage for sat" + (counter++) + ": " + memUsage + ", sent: " + dataSent);
        }
        log.info("Average mem usage: " + avgMemUsage / counter);
        log.info("Total sent: " + totalSent);

        printStationStats(stationTransactions);

        return null;
    }

    private void printStationStats(List<int[]>[] stationTransactions) {
        final var rxLimits = getStationsRxLimit();
        final var stationCount = stationTransactions.length;
        for (var i = 0; i < stationCount; i++) {
            final var satelliteCount = new HashSet<Integer>();
            var sumTransactionTime = 0L;
            for (final var transaction: stationTransactions[i]){
                satelliteCount.add(transaction[0]);
                sumTransactionTime += transaction[2] - transaction[1];
            }
            log.info(String.format(Locale.UK,"Station %d; receive time: %,d; time limit: %,d; number of satellites: %d", i, sumTransactionTime, rxLimits[i], satelliteCount.size()));
            satelliteCount.clear();
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
            if (result[i] == 0) result[i] += lastStop - lastStart;
        }
        return result;
    }

    @SuppressWarnings({"java:S135", "unused"})
    private List<int[]> calculateOtherVariants(List<int[]> stationsSniffSchedule, int satelliteId, int startTime, int stopTime) {
        final var result = new ArrayList<int[]>();
        for (final var entry : stationsSniffSchedule) {
            final var entrySatelliteId = entry[0];
            final var entryStartTime = entry[1];
            if (entrySatelliteId == satelliteId) continue;
            if (entryStartTime >= startTime && entryStartTime < stopTime) {
                result.add(entry);
            }
            if (entryStartTime >= stopTime) break;
        }

        return result;
    }

    @SuppressWarnings("All")
    private List<int[]>[] initStationsSchedules() {
        final var result = new ArrayList[connectionSchedule.getStationNames().length];
        final var allSchedules = connectionSchedule.getRecords();
        for (var i = 0; i < result.length; i++) {
            result[i] = new ArrayList<int[]>();
        }
        for (final var schedule : allSchedules) {
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

    @SuppressWarnings("SameParameterValue")
    private int calcSentVolume(List<int[]> satelliteTransactions, int currentTime) {
        var result = 0;
        for (int[] transaction : satelliteTransactions) {
            if (transaction[1] >= currentTime) break;
            if (transaction[2] < currentTime) {
                if (transaction[0] >= 0) {
                    result += (transaction[2] - transaction[1]) >> 2;
                }
            } else {
                if (transaction[0] >= 0) {
                    result += (currentTime - transaction[1]) >> 2;
                }
            }
        }
        return result;
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
            if (currentTransaction[0] > 0) return currentTransaction[2] + 1;
        }
        return minTime;
    }

    private int getCurrentTimeForStation(List<int[]> stationTransactions, int minTime) {
        if (stationTransactions.isEmpty()) return minTime;
        final var lastTransaction = stationTransactions.get(stationTransactions.size() - 1);
        return lastTransaction[2] + 1;
    }

    private int calcMemoryUsage(List<int[]> satelliteTransactions, int currentTime) {
        var result = 0;
        for (int[] transaction : satelliteTransactions) {
            if (transaction[1] >= currentTime) break;
            if (transaction[2] < currentTime) {
                if (transaction[0] < 0) {
                    result += transaction[2] - transaction[1];
                    result = Math.min(result, 2500000);
                } else {
                    result -= (transaction[2] - transaction[1]) >> 2;
                }
            } else {
                if (transaction[0] < 0) {
                    result += currentTime - transaction[1];
                    result = Math.min(result, 2500000);
                } else {
                    result -= (currentTime - transaction[1]) >> 2;
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

    private void sortConnectionSchedule() {
        Arrays.sort(connectionSchedule.getRecords(), (row1, row2) -> {
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
