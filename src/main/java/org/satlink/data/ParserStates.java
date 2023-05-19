package org.satlink.data;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ParserStates {
    SEARCH_BLOCK_START(1),
    SEARCH_DATA_START(2),
    PARSING_DATA(0);

    private final int nextState;

    public ParserStates nextState() {
        return ParserStates.values()[nextState];
    }
}
