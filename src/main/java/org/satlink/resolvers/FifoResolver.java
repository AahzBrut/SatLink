package org.satlink.resolvers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.satlink.data.Schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class FifoResolver {
    private final Schedule connectionSchedule;
    private final Schedule flybySchedule;

    @SuppressWarnings("java:S135")
    public Schedule calculate() {
        sortConnectionSchedule();
        sortFlybySchedule();
        final var connections = connectionSchedule.getRecords();
        final var satelliteTransactions = initSatelliteTransactions();
        final var stationTransactions = initStationTransactions();
        for (int[] connection : connections) {
            final var currentTimeForStation = calcCurrentTimeForStation(stationTransactions[connection[0]], connection[2]);
            final var currentTimeForSatellite = calcCurrentTimeForSatellite(satelliteTransactions[connection[1]], connection[2]);
            final var currentTime = Math.max(currentTimeForSatellite, currentTimeForStation);
            if (connection[3] < currentTime) continue;
            final var usedMemory = calcMemoryUsage(satelliteTransactions[connection[1]], currentTime);
            var maxUploadMemory = (connection[3] - currentTime) >> 2;
            if (maxUploadMemory > usedMemory) maxUploadMemory = usedMemory << 2;
            if (maxUploadMemory == 0) continue;
            maxUploadMemory += currentTime;
            addStationTransaction(stationTransactions[connection[0]], connection[1], currentTime, maxUploadMemory);
            addSatelliteTransaction(satelliteTransactions[connection[1]], connection[0], currentTime, maxUploadMemory);
        }

        return null;
    }

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
                transaction[2] = currentTime - 1;
                insertIndex = i + 1;
                splitIndex = i + 2;
                splitStation = transaction[0];
                splitStart = stopTime + 1;
                splitEnd = transaction[2];
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

    private int calcCurrentTimeForSatellite(List<int[]> stationTransactions, int minTime) {
        for (var i = stationTransactions.size() - 1; i >= 0; i--) {
            final var currentTransaction = stationTransactions.get(i);
            if (currentTransaction[0] > 0) return currentTransaction[2] + 1;
        }
        return minTime;
    }

    private int calcCurrentTimeForStation(List<int[]> stationTransactions, int minTime) {
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
                } else {
                    result -= (transaction[2] - transaction[1]) >> 2;
                }
            } else {
                if (transaction[0] < 0) {
                    result += currentTime - transaction[1];
                } else {
                    result -= currentTime - transaction[1] >> 2;
                }
            }
        }
        return Math.min(result, 2500000);
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

    private void sortConnectionSchedule() {
        Arrays.sort(connectionSchedule.getRecords(), (row1, row2) -> {
            if (row1[2] == row2[2]) return Integer.compare(row2[3], row1[3]);
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
