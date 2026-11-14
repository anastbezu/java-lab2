package org.example.model;

import org.example.exception.DatabaseException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Схема таблицы: имя и список колонок.
 * У каждой таблицы ровно один первичный ключ.
 */
public class TableSchema implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tableName;
    private final List<Column> columns;
    private Column primaryKeyColumn;

    /**
     * @param tableName имя таблицы
     * @param columns   колонки, одна из них должна быть первичным ключом
     * @throws IllegalArgumentException если колонок нет, имена повторяются или первичный ключ не один
     */
    public TableSchema(String tableName, List<Column> columns) {
        this.tableName = tableName.trim();
        this.columns = new ArrayList<>(columns);

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("В таблице должна быть хотя бы одна колонка");
        }

        List<String> seenNames = new ArrayList<>();
        int pkCount = 0;
        for (Column col : columns) {
            String lowerName = col.getName().toLowerCase();
            if (seenNames.contains(lowerName)) {
                throw new IllegalArgumentException("Колонка '" + col.getName() + "' указана два раза");
            }
            seenNames.add(lowerName);

            if (col.isPrimaryKey()) {
                pkCount++;
                primaryKeyColumn = col;
            }
        }
        if (pkCount != 1) {
            throw new IllegalArgumentException("У таблицы '" + tableName
                    + "' должен быть ровно один первичный ключ, а найдено: " + pkCount);
        }
    }

    /**
     * Возвращает имя таблицы.
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает колонки в том порядке, в котором их объявили.
     */
    public List<Column> getColumns() {
        return columns;
    }

    /**
     * Ищет колонку по имени, регистр не важен.
     *
     * @return колонка или null, если такой нет
     */
    public Column getColumn(String name) {
        String wanted = name.trim();
        for (Column col : columns) {
            if (col.getName().equalsIgnoreCase(wanted)) {
                return col;
            }
        }
        return null;
    }

    /**
     * Есть ли в таблице колонка с таким именем.
     */
    public boolean hasColumn(String name) {
        return getColumn(name) != null;
    }

    /**
     * Возвращает колонку первичного ключа.
     */
    public Column getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    /**
     * Возвращает имя колонки первичного ключа.
     */
    public String getPrimaryKeyColumnName() {
        return primaryKeyColumn.getName();
    }

    /**
     * Проверяет строку: первичный ключ заполнен, лишних колонок нет, у всех значений правильный тип.
     *
     * @throws DatabaseException если строка не подходит к таблице
     */
    public void validateRow(Row row) throws DatabaseException {
        String pkName = getPrimaryKeyColumnName();
        if (row.get(pkName) == null) {
            throw new DatabaseException("Первичный ключ '" + pkName + "' нельзя оставить пустым");
        }
        for (String colName : row.getValues().keySet()) {
            if (!hasColumn(colName)) {
                throw new DatabaseException("В таблице '" + tableName + "' нет колонки '" + colName + "'");
            }
        }
        for (Column col : columns) {
            Object value = row.get(col.getName());
            if (!col.getType().isCompatible(value)) {
                throw new DatabaseException("Колонка '" + col.getName() + "' хранит " + col.getType()
                        + ", а пришло '" + value + "'");
            }
        }
    }

    /**
     * Собирает строку для INSERT INTO t VALUES (v1, v2, ...), где значения идут по порядку колонок.
     *
     * @param rawValues значения из запроса в виде текста
     * @throws DatabaseException если значений не столько, сколько колонок, или тип не подходит
     */
    public Row createRowFromPositionalValues(List<String> rawValues) throws DatabaseException {
        if (rawValues.size() != columns.size()) {
            throw new DatabaseException("В таблице '" + tableName + "' " + columns.size()
                    + " колонок, а значений передано " + rawValues.size());
        }
        List<String> names = new ArrayList<>();
        for (Column col : columns) {
            names.add(col.getName());
        }
        return createRowFromNamedValues(names, rawValues);
    }

    /**
     * Собирает строку для INSERT INTO t (c1, c2) VALUES (v1, v2).
     * Колонки, которых нет в списке, будут NULL.
     *
     * @param columnNames имена колонок из запроса
     * @param rawValues   значения из запроса в виде текста
     * @throws DatabaseException если колонки нет, количество не совпадает или тип не подходит
     */
    public Row createRowFromNamedValues(List<String> columnNames, List<String> rawValues) throws DatabaseException {
        if (columnNames.size() != rawValues.size()) {
            throw new DatabaseException("Колонок " + columnNames.size() + ", а значений " + rawValues.size()
                    + " - их должно быть поровну");
        }
        Row row = new Row();
        for (int i = 0; i < columnNames.size(); i++) {
            String name = columnNames.get(i);
            Column col = getColumn(name);
            if (col == null) {
                throw new DatabaseException("В таблице '" + tableName + "' нет колонки '" + name + "'");
            }
            Object value;
            try {
                value = col.getType().parse(rawValues.get(i));
            } catch (IllegalArgumentException e) {
                throw new DatabaseException(e.getMessage());
            }
            row.set(col.getName(), value);
        }
        validateRow(row);
        return row;
    }

    @Override
    public String toString() {
        return tableName + columns;
    }
}
