package org.satlink.loaders;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.satlink.Main;
import org.satlink.data.Config;
import org.satlink.exceptions.ConfigLoadException;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

import static org.satlink.data.Config.*;

@Slf4j
@UtilityClass
public class ConfigLoader {
    public static Config loadConfig() {
        final var currentFolder = getCurrentFolder();
        if (currentFolder != null) {
            final var props = loadPropsFromCurrentDirectory(currentFolder);
            if (props != null) return props;
        }

        final var loader = Thread.currentThread().getContextClassLoader();
        try (final var resourceStream = loader.getResourceAsStream(CONFIG_FILE_NAME)) {
            final var props = new Properties();
            props.load(resourceStream);
            return getConfig(props);
        } catch (Exception e) {
            log.error("Failed to load config.", e);
            throw new ConfigLoadException("Failed to load config.", e);
        }
    }

    private static Config loadPropsFromCurrentDirectory(Path currentFolder) {
        final var configPath = currentFolder.resolve(CONFIG_FILE_NAME);
        log.info("Application properties path: " + configPath);
        if (configPath.toFile().isFile()){
            try (final var propsReader = Files.newBufferedReader(configPath)){
                final var props = new Properties();
                props.load(propsReader);
                return getConfig(props);
            } catch (Exception ex) {
                log.warn("Failed to read application.properties from current directory, will try to use inner.");
            }
        }
        return null;
    }

    private static Path getCurrentFolder() {
        final var mainClass = Main.class;
        final var classResource = mainClass.getResource(mainClass.getSimpleName() + ".class");
        if (classResource == null) throw new ConfigLoadException("class resource is null");

        final var url = classResource.toString();
        if (url.startsWith("jar:file:")) {
            final var path = url.replaceAll("^jar:(file:.*[.]jar)!/.*", "$1");
            try {
                return Paths.get(new URL(path).toURI()).getParent();
            } catch (Exception e) {
                throw new ConfigLoadException("Invalid Jar File URL String", e);
            }
        }
        return null;
    }

    private static Config getConfig(Properties props) {
        return new Config(
                props.getProperty(CONNECTION_SCHEDULES_PATH),
                props.getProperty(CONNECTION_SCHEDULES_FILENAME_START),
                props.getProperty(FLYBY_SCHEDULES_PATH),
                props.getProperty(FLYBY_SCHEDULES_FILENAME_START),
                props.getProperty(RESULTS_PATH),
                props.getProperty(STATISTICS_PATH),
                DateTimeFormatter.ofPattern(props.getProperty(MAIN_DATE_TIME_PATTERN), Locale.US),
                DateTimeFormatter.ofPattern(props.getProperty(STATISTICS_DATE_TIME_PATTERN)),
                Integer.parseInt(props.getProperty(TIME_STEP)));
    }
}
