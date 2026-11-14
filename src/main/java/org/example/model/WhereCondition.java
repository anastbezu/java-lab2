package org.example.model;

import org.example.exception.DatabaseException;

/**
 * Условие из WHERE вида "колонка оператор значение", например price > 20000.
 * Поддерживаются операторы =, !=, >, <, >= и <=.
 */
public class WhereCondition {
    private final String column;
    private final String operator;
    private final String value;
    private final String text;

    /**
     * @param column   имя колонки
     * @param operator оператор сравнения: =, !=, >, <, >= или <=
     * @param value    значение как в запросе, строки можно передавать в одинарных кавычках
     */
    public WhereCondition(String column, String operator, String value) {
        this.column = column;
        this.operator = operator;
        this.value = value;
        boolean quoted = value.length() >= 2 && value.startsWith("'") && value.endsWith("'");
        if (quoted) {
            this.text = value.substring(1, value.length() - 1);
        } else {
            this.text = value;
        }
    }

    /**
     * Возвращает имя колонки из условия.
     */
    public String getColumn() {
        return column;
    }

    /**
     * Возвращает оператор сравнения.
     */
    public String getOperator() {
        return operator;
    }

    /**
     * Возвращает значение так, как оно написано в запросе, вместе с кавычками.
     * Его можно передавать в DataType.parse: так строка 'NULL' не перепутается с настоящим NULL.
     */
    public String getValue() {
        return value;
    }

    /**
     * Проверяет, что условие подходит к таблице: колонка есть, значение подходит к её типу,
     * а >, <, >= и <= используются только с числами.
     *
     * @throws DatabaseException если условие к таблице не подходит
     */
    public void check(TableSchema schema) throws DatabaseException {
        Column col = schema.getColumn(column);
        if (col == null) {
            throw new DatabaseException("В таблице '" + schema.getTableName() + "' нет колонки '" + column
                    + "', проверьте условие WHERE");
        }
        DataType type = col.getType();
        boolean isCompare = !operator.equals("=") && !operator.equals("!=");
        boolean isNumeric = type == DataType.INT || type == DataType.FLOAT;
        if (isCompare && !isNumeric) {
            throw new DatabaseException("Через " + operator + " можно сравнивать только числа, а колонка "
                    + col.getName() + " имеет тип " + type);
        }
        try {
            type.parse(value);
        } catch (IllegalArgumentException e) {
            throw new DatabaseException(e.getMessage());
        }
    }

    /**
     * Проверяет, подходит ли строка под условие. Если в колонке NULL, строка не подходит.
     *
     * @param row проверяемая строка
     * @return true, если строка подходит
     */
    public boolean matches(Row row) {
        Object cell = row.get(column);
        if (cell == null) {
            return false;
        }

        int comparison;
        if (cell instanceof Number) {
            double expected;
            try {
                expected = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return false;
            }
            double actual = ((Number) cell).doubleValue();
            comparison = Double.compare(actual, expected);
        } else if (cell instanceof Boolean) {
            Boolean expected = Boolean.valueOf(text);
            if (cell.equals(expected)) {
                comparison = 0;
            } else {
                comparison = 1;
            }
        } else {
            comparison = cell.toString().compareTo(text);
        }

        switch (operator) {
            case "=":
                return comparison == 0;
            case "!=":
                return comparison != 0;
            case ">":
                return comparison > 0;
            case "<":
                return comparison < 0;
            case ">=":
                return comparison >= 0;
            case "<=":
                return comparison <= 0;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return column + " " + operator + " " + value;
    }
}
