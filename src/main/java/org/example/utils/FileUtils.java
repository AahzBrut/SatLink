package org.example.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
@UtilityClass
public class FileUtils {

    public static List<File> getFilteredFilesFromDirectory(Path directoryPath, Predicate<File> filter) {
        return Stream
                .of(Objects
                        .requireNonNull(directoryPath
                                .toFile()
                                .listFiles()))
                .filter(filter)
                .toList();
    }
}
