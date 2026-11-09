package com.dkozel.velocitylimiter.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class JsonFieldNames {

    private JsonFieldNames() {
    }

    // the name a client uses for a Java property: its @JsonProperty, else the Java name itself
    public static String of(Class<?> type, String property) {
        try {
            JsonProperty json = type.getDeclaredField(property).getAnnotation(JsonProperty.class);
            return json != null ? json.value() : property;
        } catch (NoSuchFieldException notAField) {
            return property;
        }
    }
}
