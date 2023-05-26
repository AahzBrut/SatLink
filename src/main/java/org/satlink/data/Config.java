package org.satlink.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@AllArgsConstructor
@Accessors(chain = true)
@ToString
@SuppressWarnings("ClassCanBeRecord")
public class Config {
    public static final String CONFIG_FILE_NAME = "application.properties";
    public static final String CONNECTION_SCHEDULES_PATH = "connectionSchedulesPath";
    public static final String CONNECTION_SCHEDULES_FILENAME_START = "connectionScheduleFileNameStart";
    public static final String FLYBY_SCHEDULES_PATH = "flybySchedulesPath";
    public static final String FLYBY_SCHEDULES_FILENAME_START = "flybyScheduleFileNameStart";
    public static final String RESULTS_PATH = "resultsPath";
    public static final String STATISTICS_PATH = "statisticsPath";
    public static final String TIME_STEP = "timeStep";
    public static final String MAIN_DATE_TIME_PATTERN = "mainDateTimePattern";
    public static final String STATISTICS_DATE_TIME_PATTERN = "statisticsDateTimePattern";

    public final String connectionSchedulesPath;
    public final String connectionScheduleFileNameStart;
    public final String flybySchedulesPath;
    public final String flybyScheduleFileNameStart;
    public final String resultsPath;
    public final String statisticsPath;
    public final DateTimeFormatter mainDateTimeFormatter;
    public final DateTimeFormatter statisticsDateTimeFormatter;
    public final int timeStep;
}
