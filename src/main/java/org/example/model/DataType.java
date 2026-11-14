package org.example.model;

import java.time.LocalDate;

/**
 * Типы, которые можно хранить в колонках:
 * INT (Integer), FLOAT (Double), STRING (String), DATE (LocalDate, формат YYYY-MM-DD)
 * и BOOLEAN (TRUE или FALSE).
 */
public enum DataType {
    INT,
    FLOAT,
    STRING,
    DATE,
    BOOLEAN;

    /**
     * Переводит текст из запроса в значение нужного типа.
     * Например, для INT "25" станет числом 25, а для STRING "'BMW'" станет строкой BMW.
     *
     * @param rawValue значение из запроса
     * @return значение нужного типа или null, если передали NULL
     * @throws IllegalArgumentException если значение не подходит к типу
     */
    public Object parse(String rawValue) {
        if (rawValue == null || rawValue.trim().equalsIgnoreCase("NULL")) {
            return null;
        }
        String value = removeQuotes(rawValue.trim());

        try {
            switch (this) {
                case INT:
                    return Integer.parseInt(value);
                case FLOAT:
                    return Double.parseDouble(value);
                case STRING:
                    return value;
                case DATE:
                    return LocalDate.parse(value);
                case BOOLEAN:
                    if (value.equalsIgnoreCase("TRUE")) {
                        return true;
                    }
                    if (value.equalsIgnoreCase("FALSE")) {
                        return false;
                    }
                    throw new IllegalArgumentException("ожидается TRUE или FALSE");
                default:
                    throw new IllegalArgumentException("неизвестный тип");
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Не получается записать '" + rawValue + "' в колонку типа " + this);
        }
    }

    /**
     * Проверяет, что значение подходит к этому типу. NULL подходит к любому типу.
     *
     * @param value проверяемое значение
     * @return true, если значение подходит
     */
    public boolean isCompatible(Object value) {
        if (value == null) {
            return true;
        }
        switch (this) {
            case INT:
                return value instanceof Integer;
            case FLOAT:
                return value instanceof Double;
            case STRING:
                return value instanceof String;
            case DATE:
                return value instanceof LocalDate;
            case BOOLEAN:
                return value instanceof Boolean;
            default:
                return false;
        }
    }

    private static String removeQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
