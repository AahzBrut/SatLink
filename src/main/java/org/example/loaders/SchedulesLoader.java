package org.example.loaders;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.example.utils.FileUtils;
import org.example.data.FlybyScheduleRecord;
import org.example.data.ParserStates;
import org.example.data.SourceScheduleRecord;
import org.example.exceptions.ConnectionSchedulesParserException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@UtilityClass
public class SchedulesLoader {
    private static final String ERROR_TEXT = "Failed to parse file ";
    private static final String HEADER_MARKER = "-----";

    public static List<SourceScheduleRecord> loadConnectionSchedules(Path directoryPath) {
        final var fileList = FileUtils.getFilteredFilesFromDirectory(directoryPath, SchedulesLoader::connectionScheduleFileFilter);
        final var result = new ArrayList<SourceScheduleRecord>();

        for (final var file : fileList){
            result.addAll(parseConnectionScheduleFile(file));
        }
        return result;
    }

    public static List<FlybyScheduleRecord> loadFlybySchedules(Path directoryPath) {
        final var fileList = FileUtils.getFilteredFilesFromDirectory(directoryPath, SchedulesLoader::flybyScheduleFileFilter);
        final var result = new ArrayList<FlybyScheduleRecord>();

        for (final var file : fileList){
            result.addAll(parseFlybyScheduleFile(file));
        }
        return result;
    }

    @SuppressWarnings("Duplicates")
    private static List<SourceScheduleRecord> parseConnectionScheduleFile(File file) {
        try {
            final var dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu HH:mm:ss.SSS", Locale.US);
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
                        final var startTime = LocalDateTime.parse(currentLine.substring(28, 52).trim(), dateTimeFormatter);
                        final var stopTime = LocalDateTime.parse(currentLine.substring(56, 80).trim(), dateTimeFormatter);
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
    private static List<FlybyScheduleRecord> parseFlybyScheduleFile(File file) {
        try {
            final var dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu HH:mm:ss.SSS", Locale.US);
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
                        final var startTime = LocalDateTime.parse(currentLine.substring(28, 52).trim(), dateTimeFormatter);
                        final var stopTime = LocalDateTime.parse(currentLine.substring(56, 80).trim(), dateTimeFormatter);
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

    private static boolean connectionScheduleFileFilter(File file) {
        return file.getName().startsWith("Facility-");
    }

    private static boolean flybyScheduleFileFilter(File file) {
        return file.getName().startsWith("AreaTarget-Russia-To-");
    }
}
