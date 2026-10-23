package com.casamix.assistant.service;

import com.casamix.assistant.domain.Intent;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {
    private static final Pattern SENSITIVE = Pattern.compile("(?i)reclama[cç][aã]o formal|fraude");
    private static final Pattern REFUND = Pattern.compile(
            "(?i)reembolso.{0,30}?(?:R\\$\\s*)?([0-9][0-9.,]*)");
    private static final Pattern HUMAN = Pattern.compile("(?i)atendente|humano|pessoa|falar com alguém");
    private static final Pattern EXCHANGE = Pattern.compile(
            "(?i)troca|trocar|defeito|avaria|item errado|arrependimento|status do pedido|pedido\\s+[\\w-]+");
    private static final Pattern EXCHANGE_ACTION = Pattern.compile(
            "(?i)quero trocar|solicitar troca|abrir troca|trocar pedido|status do pedido");
    private static final Pattern POLICY = Pattern.compile(
            "(?i)pol[ií]tica|devolu[cç][aã]o|devolver|prazo|entrega|garantia|dias corridos|"
                    + "reembolso|quantos dias|o que fazer|como funciona|quando posso|regras?");

    public Intent classify(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (SENSITIVE.matcher(text).find() || refundAboveThreshold(text)) {
            return Intent.HUMAN;
        }
        if (HUMAN.matcher(text).find()) {
            return Intent.HUMAN;
        }
        if (POLICY.matcher(text).find()) {
            return Intent.POLICY;
        }
        if (EXCHANGE_ACTION.matcher(text).find()) {
            return Intent.EXCHANGE;
        }
        if (EXCHANGE.matcher(text).find()) {
            return Intent.EXCHANGE;
        }
        return Intent.OUT_OF_SCOPE;
    }

    public boolean isPromptInjection(String message) {
        return Pattern.compile("(?i)ignore\\s+(?:todas\\s+)?(?:as\\s+)?instru[cç][oõ]es|"
                + "ignore previous instructions|revele o prompt|mostre sua chave|"
                + "act as (?:a|an) unrestricted").matcher(message).find();
    }

    private boolean refundAboveThreshold(String text) {
        Matcher matcher = REFUND.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        String amount = matcher.group(1);
        if (amount.matches("\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?")) {
            amount = amount.replace(".", "").replace(',', '.');
        } else if (amount.contains(",")) {
            amount = amount.replace(',', '.');
        }
        try {
            return Double.parseDouble(amount) > 1500.0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
