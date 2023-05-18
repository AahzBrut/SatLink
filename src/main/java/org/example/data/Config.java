package org.example.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@AllArgsConstructor
@Accessors(chain = true)
@ToString
@SuppressWarnings("ClassCanBeRecord")
public class Config {
    public final String connectionSchedulesPath;
    public final String flybySchedulesPath;
}
