package rs.ac.bg.etf.kdp.tuple;

import java.io.Serializable;
import java.util.Optional;

public class FieldParser {
    public static Optional<? extends Serializable> parse(String field) {
        if(field == null) return Optional.of("null");
        Optional<? extends Serializable> value;
        if ((value = parseString(field)).isPresent()) return value;
        if ((value = parseCharacter(field)).isPresent()) return value;
        if ((value = parseClass(field)).isPresent()) return value;
        if ((value = parseInteger(field)).isPresent()) return value;
        if ((value = parseDouble(field)).isPresent()) return value;
        if ((value = parseBoolean(field)).isPresent()) return value;
        return Optional.of(field);
    }

    private static Optional<String> parseString(String field) {
        if (!field.startsWith("\"") || !field.endsWith("\"")) {
            return Optional.empty();
        }
        final var value = field.substring(1, field.length() - 1);
        return Optional.of(value);
    }

    private static Optional<Character> parseCharacter(String field) {
        if (!field.startsWith("'") || !field.endsWith("'")) {
            return Optional.empty();
        }
        if (field.length() != 3) {
            return Optional.empty();
        }
        return Optional.of(field.charAt(1));
    }

    private static Optional<Class<?>> classFromName(String name) {
        try {
            return Optional.of(Class.forName(name));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    private static Optional<Class<?>> parseClass(String field) {
        if (!field.startsWith("?")) return Optional.empty();
        final var baseClassName = field.substring(1);
        if (baseClassName.startsWith("java.lang.")) {
            return classFromName(baseClassName);
        }

        return classFromName("java.lang." + baseClassName);
    }

    private static Optional<Integer> parseInteger(String field) {
        try {
            final var value = Integer.valueOf(field);
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Double> parseDouble(String field) {
        try {
            final var value = Double.valueOf(field);
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> parseBoolean(String field) {
        switch (field.toLowerCase()) {
            case "true":
            case "false":
                return Optional.of(Boolean.valueOf(field));
            default:
                return Optional.empty();
        }
    }
}
