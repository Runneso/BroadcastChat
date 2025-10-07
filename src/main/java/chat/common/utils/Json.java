package chat.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static String toJson(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromFile(String filePath, Class<T> clazz) {
        try {
            return MAPPER.readValue(new java.io.File(filePath), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
