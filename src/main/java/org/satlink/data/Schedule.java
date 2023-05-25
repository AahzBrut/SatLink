package org.satlink.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class Schedule {
    private final LocalDateTime startInstant;
    private final String[] stationNames;
    private final String[] satelliteNames;
    private final int[][] records;
}
