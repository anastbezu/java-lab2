package org.example.model;

import java.io.Serializable;

/**
 * Колонка таблицы: имя, тип и ключи.
 * Колонка может быть первичным ключом или ссылаться на другую таблицу (внешний ключ).
 */
public class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final String foreignKeyTable;
    private final String foreignKeyColumn;

    /**
     * Обычная колонка без ключей.
     */
    public Column(String name, DataType type) {
        this(name, type, false, null, null);
    }

    /**
     * Колонка, которая может быть первичным ключом.
     */
    public Column(String name, DataType type, boolean primaryKey) {
        this(name, type, primaryKey, null, null);
    }

    /**
     * Колонка-внешний ключ, например manufacturer_id REFERENCES manufacturers(id).
     *
     * @param foreignKeyTable  таблица, на которую ссылаемся
     * @param foreignKeyColumn первичный ключ этой таблицы
     */
    public Column(String name, DataType type, String foreignKeyTable, String foreignKeyColumn) {
        this(name, type, false, foreignKeyTable, foreignKeyColumn);
    }

    private Column(String name, DataType type, boolean primaryKey, String foreignKeyTable, String foreignKeyColumn) {
        this.name = name.trim();
        this.type = type;
        this.primaryKey = primaryKey;
        this.foreignKeyTable = foreignKeyTable;
        this.foreignKeyColumn = foreignKeyColumn;
    }

    /**
     * Возвращает имя колонки.
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает тип колонки.
     */
    public DataType getType() {
        return type;
    }

    /**
     * Является ли колонка первичным ключом.
     */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Ссылается ли колонка на другую таблицу.
     */
    public boolean isForeignKey() {
        return foreignKeyTable != null;
    }

    /**
     * Возвращает таблицу, на которую ссылается внешний ключ, или null.
     */
    public String getForeignKeyTable() {
        return foreignKeyTable;
    }

    /**
     * Возвращает колонку, на которую ссылается внешний ключ, или null.
     */
    public String getForeignKeyColumn() {
        return foreignKeyColumn;
    }

    @Override
    public String toString() {
        String text = name + " " + type;
        if (primaryKey) {
            text += " PRIMARY KEY";
        }
        if (isForeignKey()) {
            text += " REFERENCES " + foreignKeyTable + "(" + foreignKeyColumn + ")";
        }
        return text;
    }
}
