package org.example.model;

import java.io.Serializable;

/**
 * Описание одной колонки таблицы: имя, тип, является ли она первичным ключом
 * и (если это внешний ключ) на какую таблицу она ссылается.
 */
public class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final String foreignKeyTable;
    private final String foreignKeyColumn;

    /**
     * Обычная колонка.
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
     * Колонка-внешний ключ: ссылается на первичный ключ другой таблицы.
     *
     * @param foreignKeyTable  таблица, на которую ссылаемся (например, manufacturers)
     * @param foreignKeyColumn её первичный ключ (например, id)
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

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isForeignKey() {
        return foreignKeyTable != null;
    }

    public String getForeignKeyTable() {
        return foreignKeyTable;
    }

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
