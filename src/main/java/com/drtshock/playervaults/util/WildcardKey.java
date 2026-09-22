package com.drtshock.playervaults.util;

import org.bukkit.NamespacedKey;

import java.util.Locale;
import java.util.regex.Pattern;

public final class WildcardKey {
    private final Pattern namespacePattern;
    private final Pattern keyPattern;

    private WildcardKey(Pattern namespacePattern, Pattern keyPattern) {
        this.namespacePattern = namespacePattern;
        this.keyPattern = keyPattern;
    }

    public boolean matches(NamespacedKey key) {
        return namespacePattern.matcher(key.getNamespace()).matches() && keyPattern.matcher(key.getKey()).matches();
    }

    public static WildcardKey of(String pattern) {
        String[] parts = pattern.split(":", 2);
        if (parts.length == 1) {
            return of(NamespacedKey.MINECRAFT, parts[0]);
        }
        return of(parts[0], parts[1]);
    }

    private static WildcardKey of(String namespace, String key) {
        return new WildcardKey(patternizer(namespace), patternizer(key));
    }

    private static Pattern patternizer(String pattern) {
        pattern = pattern.toLowerCase(Locale.ROOT);
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder(pattern.length() + 8);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            if (!parts[i].isEmpty()) {
                regex.append(Pattern.quote(parts[i]));
            }
        }
        return Pattern.compile(regex.toString());
    }
}