package org.satlink.loaders;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.satlink.data.Config;
import org.satlink.exceptions.ConfigLoadException;

import java.util.Properties;

@Slf4j
@UtilityClass
public class ConfigLoader {
    private static final String CONFIG_FILE_NAME = "application.properties";
    private static final String  CONNECTION_SCHEDULES_PATH = "connectionSchedulesPath";
    private static final String FLYBY_SCHEDULES_PATH = "flybySchedulesPath";

    public static Config loadConfig() {
        final var loader = Thread.currentThread().getContextClassLoader();
        try(final var resourceStream = loader.getResourceAsStream(CONFIG_FILE_NAME)) {
            final var props = new Properties();
            props.load(resourceStream);
            return new Config(props.getProperty(CONNECTION_SCHEDULES_PATH), props.getProperty(FLYBY_SCHEDULES_PATH));
        } catch (Exception e) {
            log.error("Failed to load config.", e);
            throw new ConfigLoadException("Failed to load config.", e);
        }
    }
}
