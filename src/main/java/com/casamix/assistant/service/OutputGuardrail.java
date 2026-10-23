package com.casamix.assistant.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OutputGuardrail {
    private static final Pattern HTML = Pattern.compile("(?is)<[^>]*>");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?55\\s?)?(?:\\(?\\d{2}\\)?\\s?)?\\d{4,5}[-\\s]?\\d{4}(?!\\d)");

    public String sanitize(String output) {
        String safe = HTML.matcher(output).replaceAll("");
        safe = EMAIL.matcher(safe).replaceAll("[dado removido]");
        return PHONE.matcher(safe).replaceAll("[dado removido]").trim();
    }
}
