package org.satlink.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class SatelliteParams {
    private final int maxTimeAmount;
    private final int transmitRatio;
    private final int bandwidth;
}
