package org.satlink.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.satlink.data.Config;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

@Slf4j
@UtilityClass
public class FileUtils {
    public static List<File> getFilteredFilesFromDirectory(Config config, Path directoryPath, BiPredicate<File, Config> filter) {
        return Stream
                .of(Objects
                        .requireNonNull(directoryPath
                                .toFile()
                                .listFiles()))
                .filter(f -> filter.test(f, config))
                .toList();
    }
}
