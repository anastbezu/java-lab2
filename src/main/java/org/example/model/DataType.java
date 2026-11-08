package org.example.model;

import java.time.LocalDate;

/**
 * Типы данных, которые можно хранить в колонках таблицы.
 * <ul>
 *     <li>INT — целое число ({@link Integer})</li>
 *     <li>FLOAT — число с плавающей точкой ({@link Double})</li>
 *     <li>STRING — строка ({@link String})</li>
 *     <li>DATE — дата в формате YYYY-MM-DD ({@link LocalDate})</li>
 *     <li>BOOLEAN — логическое значение TRUE/FALSE ({@link Boolean})</li>
 * </ul>
 */
public enum DataType {
    INT,
    FLOAT,
    STRING,
    DATE,
    BOOLEAN;

    /**
     * Превращает текст из SQL-запроса в Java-объект нужного типа.
     * Например, для INT строка "25" станет числом 25, для STRING "'BMW'" станет BMW.
     *
     * @param rawValue значение из запроса
     * @return объект нужного типа или null, если передано NULL
     * @throws IllegalArgumentException если значение нельзя преобразовать к типу
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
            throw new IllegalArgumentException("Значение '" + rawValue + "' не подходит к типу " + this);
        }
    }

    /**
     * Проверяет, что значение имеет правильный Java-тип для этой колонки.
     *
     * @param value проверяемое значение
     * @return true, если значение подходит (null подходит всегда)
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

    /**
     * Сравнивает два значения одного типа (для условий WHERE с &lt; и &gt;).
     *
     * @return отрицательное число, если a &lt; b; 0, если равны; положительное, если a &gt; b
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int compare(Object a, Object b) {
        return ((Comparable) a).compareTo(b);
    }

    private static String removeQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
