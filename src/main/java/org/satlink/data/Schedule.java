package org.satlink.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class Schedule {
    private final String[] stationNames;
    private final String[] satelliteNames;
    private final int[][] records;
}
