package org.example.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Одна строка (запись) таблицы: пары «имя колонки → значение».
 * Имена колонок не зависят от регистра: {@code get("Brand")} и {@code get("brand")} — одно и то же.
 */
public class Row implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values = new LinkedHashMap<>();

    /**
     * Возвращает значение колонки или null, если его нет.
     */
    public Object get(String columnName) {
        return values.get(columnName.trim().toLowerCase());
    }

    /**
     * Записывает значение в колонку.
     */
    public void set(String columnName, Object value) {
        values.put(columnName.trim().toLowerCase(), value);
    }

    /**
     * Возвращает все пары «колонка → значение» (только для чтения).
     */
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Создаёт независимую копию строки.
     */
    public Row copy() {
        Row copy = new Row();
        copy.values.putAll(values);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Row)) {
            return false;
        }
        return values.equals(((Row) o).values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "Row" + values;
    }
}
