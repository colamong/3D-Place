package com.colombus.snapshot.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ScriptLoader {
    public static String loadLuaScript(String path) {
        Resource resource = new ClassPathResource(path);
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Lua 스크립트를 읽는 중 오류 발생: " + path, e);
        }
    }
}
