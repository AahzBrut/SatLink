package org.example.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Accessors(chain = true)
@SuppressWarnings("ClassCanBeRecord")
public class SourceScheduleRecord {
    private final String stationName;
    private final String satelliteName;
    private final Long access;
    private final LocalDateTime startTime;
    private final LocalDateTime stopTime;
    private final Double duration;
}
