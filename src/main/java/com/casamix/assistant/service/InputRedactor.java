package com.casamix.assistant.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class InputRedactor {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?55\\s?)?(?:\\(?\\d{2}\\)?\\s?)?\\d{4,5}[-\\s]?\\d{4}(?!\\d)");

    public String redact(String input) {
        return PHONE.matcher(EMAIL.matcher(input).replaceAll("[EMAIL REMOVIDO]"))
                .replaceAll("[TELEFONE REMOVIDO]");
    }
}
