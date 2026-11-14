package org.example.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Одна строка таблицы: набор пар "колонка -> значение".
 * Регистр в именах колонок не важен, get("Brand") и get("brand") вернут одно и то же.
 */
public class Row implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> values = new LinkedHashMap<>();

    /**
     * Возвращает значение колонки или null, если его нет.
     */
    public Object get(String columnName) {
        String key = columnName.trim().toLowerCase();
        return values.get(key);
    }

    /**
     * Записывает значение в колонку.
     */
    public void set(String columnName, Object value) {
        String key = columnName.trim().toLowerCase();
        values.put(key, value);
    }

    /**
     * Возвращает все значения строки. Менять их через этот метод нельзя.
     */
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Делает копию строки, которую можно менять, не трогая оригинал.
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
        Row other = (Row) o;
        return values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "Row" + values;
    }
}
