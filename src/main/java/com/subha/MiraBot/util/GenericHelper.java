package com.subha.MiraBot.util;


import com.subha.MiraBot.dto.OutputContext;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GenericHelper {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("/sessions/(.*?)/contexts/");

    public static String extractSessionId(List<OutputContext> outputContexts) {
        if (outputContexts == null || outputContexts.isEmpty()) {
            return "";
        }
        String contextName = outputContexts.get(0).getName();
        Matcher matcher = SESSION_ID_PATTERN.matcher(contextName);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String getStrFromFoodMap(Map<String, Integer> foodMap) {
        return foodMap.entrySet().stream()
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .collect(Collectors.joining(", "));
    }
}
