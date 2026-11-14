package org.example.sql;

import org.example.exception.SqlException;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.TableSchema;
import org.example.model.WhereCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * Разбирает текст SQL-запроса и превращает его в команду.
 * Поддерживаются SELECT, INSERT, UPDATE, DELETE, CREATE TABLE, CREATE INDEX и DROP TABLE.
 * Строки пишутся в одинарных кавычках, в WHERE можно одно простое условие.
 */
public class SqlParser {

    /**
     * Разбирает запрос.
     *
     * @param sql текст запроса, точка с запятой в конце не обязательна
     * @return команда, которую можно выполнить
     * @throws SqlException если запрос написан неправильно
     */
    public QueryCommand parse(String sql) {
        if (sql == null) {
            throw new SqlException("Запрос пустой - введите SQL-команду");
        }
        String text = sql.trim();
        while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        if (text.isEmpty()) {
            throw new SqlException("Запрос пустой - введите SQL-команду");
        }

        String upper = text.toUpperCase();
        if (upper.startsWith("SELECT ")) {
            return parseSelect(text);
        }
        if (upper.startsWith("INSERT INTO ")) {
            return parseInsert(text);
        }
        if (upper.startsWith("UPDATE ")) {
            return parseUpdate(text);
        }
        if (upper.startsWith("DELETE FROM ")) {
            return parseDelete(text);
        }
        if (upper.startsWith("CREATE TABLE ")) {
            return parseCreateTable(text);
        }
        if (upper.startsWith("CREATE INDEX ")) {
            return parseCreateIndex(text);
        }
        if (upper.startsWith("DROP TABLE ")) {
            return parseDropTable(text);
        }
        throw new SqlException("Не понял команду. Поддерживаются SELECT, INSERT, UPDATE, DELETE, "
                + "CREATE TABLE, CREATE INDEX и DROP TABLE");
    }

    /**
     * SELECT колонки FROM таблица [WHERE условие]
     */
    private QueryCommand parseSelect(String text) {
        int fromIndex = findKeyword(text, "FROM");
        if (fromIndex == -1) {
            throw new SqlException("В SELECT не хватает FROM, пример: SELECT * FROM cars");
        }
        String columnsText = text.substring(6, fromIndex).trim();
        String rest = text.substring(fromIndex + 4).trim();

        String tableName = rest;
        WhereCondition condition = null;
        int whereIndex = findKeyword(rest, "WHERE");
        if (whereIndex != -1) {
            tableName = rest.substring(0, whereIndex).trim();
            String conditionText = rest.substring(whereIndex + 5);
            condition = parseWhere(conditionText);
        }
        checkName(tableName, "таблицы");

        if (columnsText.isEmpty()) {
            throw new SqlException("После SELECT нужно перечислить колонки или поставить *");
        }
        List<String> columns = splitByComma(columnsText);
        List<String> seen = new ArrayList<>();
        for (String column : columns) {
            if (column.equals("*")) {
                if (columns.size() > 1) {
                    throw new SqlException("Звёздочку нельзя смешивать с другими колонками");
                }
                continue;
            }
            checkName(column, "колонки");
            String lower = column.toLowerCase();
            if (seen.contains(lower)) {
                throw new SqlException("Колонка " + column + " указана в SELECT два раза");
            }
            seen.add(lower);
        }
        return new SelectCommand(tableName, columns, condition);
    }

    /**
     * INSERT INTO таблица [(колонки)] VALUES (значения), (значения), ...
     */
    private QueryCommand parseInsert(String text) {
        String rest = text.substring(12).trim();
        int valuesIndex = findKeyword(rest, "VALUES");
        if (valuesIndex == -1) {
            throw new SqlException("В INSERT не хватает VALUES, пример: INSERT INTO cars VALUES (1, 'BMW')");
        }
        String target = rest.substring(0, valuesIndex).trim();
        String valuesText = rest.substring(valuesIndex + 6).trim();

        String tableName = target;
        List<String> columns = null;
        int openIndex = target.indexOf('(');
        if (openIndex != -1) {
            if (!target.endsWith(")")) {
                throw new SqlException("Не закрыта скобка со списком колонок");
            }
            tableName = target.substring(0, openIndex).trim();
            String columnsText = target.substring(openIndex + 1, target.length() - 1);
            columns = splitByComma(columnsText);
            for (String column : columns) {
                checkName(column, "колонки");
            }
        }
        checkName(tableName, "таблицы");

        List<List<String>> rows = new ArrayList<>();
        for (String group : splitValueGroups(valuesText)) {
            List<String> values = splitByComma(group);
            for (String value : values) {
                checkValue(value);
            }
            if (columns != null && columns.size() != values.size()) {
                throw new SqlException("Колонок " + columns.size() + ", а значений " + values.size()
                        + " - их должно быть поровну");
            }
            rows.add(values);
        }
        return new InsertCommand(tableName, columns, rows);
    }

    /**
     * UPDATE таблица SET колонка = значение WHERE условие
     */
    private QueryCommand parseUpdate(String text) {
        String rest = text.substring(7).trim();
        int setIndex = findKeyword(rest, "SET");
        if (setIndex == -1) {
            throw new SqlException("В UPDATE не хватает SET, пример: UPDATE cars SET price = 100 WHERE id = 1");
        }
        String tableName = rest.substring(0, setIndex).trim();
        checkName(tableName, "таблицы");

        String afterSet = rest.substring(setIndex + 3).trim();
        int whereIndex = findKeyword(afterSet, "WHERE");
        if (whereIndex == -1) {
            throw new SqlException("UPDATE без WHERE не поддерживается - добавьте условие, например WHERE id = 1");
        }
        String assignment = afterSet.substring(0, whereIndex).trim();
        if (splitByComma(assignment).size() > 1) {
            throw new SqlException("В SET можно поменять только одну колонку за раз");
        }
        int equalsIndex = assignment.indexOf('=');
        if (equalsIndex == -1) {
            throw new SqlException("После SET нужно присваивание вида колонка = значение");
        }
        String column = assignment.substring(0, equalsIndex).trim();
        String value = assignment.substring(equalsIndex + 1).trim();
        checkName(column, "колонки");
        checkValue(value);

        String conditionText = afterSet.substring(whereIndex + 5);
        WhereCondition condition = parseWhere(conditionText);
        return new UpdateCommand(tableName, column, value, condition);
    }

    /**
     * DELETE FROM таблица WHERE условие
     */
    private QueryCommand parseDelete(String text) {
        String rest = text.substring(12).trim();
        int whereIndex = findKeyword(rest, "WHERE");
        if (whereIndex == -1) {
            throw new SqlException("DELETE без WHERE не поддерживается - добавьте условие, например WHERE id = 1");
        }
        String tableName = rest.substring(0, whereIndex).trim();
        checkName(tableName, "таблицы");
        String conditionText = rest.substring(whereIndex + 5);
        WhereCondition condition = parseWhere(conditionText);
        return new DeleteCommand(tableName, condition);
    }

    /**
     * CREATE TABLE таблица (колонка ТИП [PRIMARY KEY | REFERENCES таблица(колонка)], ...)
     */
    private QueryCommand parseCreateTable(String text) {
        int openIndex = text.indexOf('(');
        if (openIndex == -1 || !text.endsWith(")")) {
            throw new SqlException("Колонки нужно перечислить в скобках, пример: "
                    + "CREATE TABLE cars (id INT PRIMARY KEY, brand STRING)");
        }
        String tableName = text.substring(13, openIndex).trim();
        checkName(tableName, "таблицы");

        String columnsText = text.substring(openIndex + 1, text.length() - 1);
        List<Column> columns = new ArrayList<>();
        for (String definition : splitByComma(columnsText)) {
            Column column = parseColumn(definition);
            columns.add(column);
        }

        TableSchema schema;
        try {
            schema = new TableSchema(tableName, columns);
        } catch (IllegalArgumentException e) {
            throw new SqlException(e.getMessage());
        }
        return new CreateTableCommand(schema);
    }

    /**
     * Разбирает описание одной колонки, например "price FLOAT" или "id INT PRIMARY KEY".
     */
    private Column parseColumn(String definition) {
        String[] parts = definition.trim().split("\\s+", 3);
        if (parts.length < 2) {
            throw new SqlException("Колонку нужно описать как имя и тип, например price FLOAT, а тут: '"
                    + definition.trim() + "'");
        }
        String name = parts[0];
        checkName(name, "колонки");

        DataType type;
        try {
            type = DataType.valueOf(parts[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SqlException("Неизвестный тип '" + parts[1] + "'. Есть INT, FLOAT, STRING, DATE и BOOLEAN");
        }
        if (parts.length == 2) {
            return new Column(name, type);
        }

        String constraint = parts[2].trim();
        String upperConstraint = constraint.toUpperCase();
        if (upperConstraint.equals("PRIMARY KEY")) {
            return new Column(name, type, true);
        }
        if (upperConstraint.startsWith("REFERENCES ")) {
            String target = constraint.substring(11).trim();
            int openIndex = target.indexOf('(');
            if (openIndex == -1 || !target.endsWith(")")) {
                throw new SqlException("Внешний ключ пишется так: REFERENCES manufacturers(id)");
            }
            String refTable = target.substring(0, openIndex).trim();
            String refColumn = target.substring(openIndex + 1, target.length() - 1).trim();
            checkName(refTable, "таблицы");
            checkName(refColumn, "колонки");
            return new Column(name, type, refTable, refColumn);
        }
        throw new SqlException("Не понял '" + constraint + "' у колонки " + name
                + ". Можно указать PRIMARY KEY или REFERENCES таблица(колонка)");
    }

    /**
     * CREATE INDEX имя ON таблица (колонка)
     */
    private QueryCommand parseCreateIndex(String text) {
        String rest = text.substring(13).trim();
        int onIndex = findKeyword(rest, "ON");
        if (onIndex == -1) {
            throw new SqlException("В CREATE INDEX не хватает ON, пример: CREATE INDEX idx_brand ON cars (brand)");
        }
        String indexName = rest.substring(0, onIndex).trim();
        String target = rest.substring(onIndex + 2).trim();
        int openIndex = target.indexOf('(');
        if (openIndex == -1 || !target.endsWith(")")) {
            throw new SqlException("Колонку для индекса нужно указать в скобках: ON cars (brand)");
        }
        String tableName = target.substring(0, openIndex).trim();
        String columnName = target.substring(openIndex + 1, target.length() - 1).trim();
        checkName(indexName, "индекса");
        checkName(tableName, "таблицы");
        checkName(columnName, "колонки");
        return new CreateIndexCommand(indexName, tableName, columnName);
    }

    /**
     * DROP TABLE таблица
     */
    private QueryCommand parseDropTable(String text) {
        String tableName = text.substring(11).trim();
        checkName(tableName, "таблицы");
        return new DropTableCommand(tableName);
    }

    /**
     * Превращает текст условия WHERE в объект условия.
     *
     * @param whereClause условие, например "price > 20000"
     * @return условие для проверки строк
     * @throws SqlException если условие написано неправильно
     */
    public static WhereCondition parseWhere(String whereClause) {
        String[] parts = parseConditionParts(whereClause);
        String column = parts[0];
        String operator = parts[1];
        String value = parts[2];
        return new WhereCondition(column, operator, value);
    }

    /**
     * Делит условие WHERE на колонку, оператор и значение. Кавычки у строки остаются.
     *
     * @throws SqlException если условие написано неправильно
     */
    static String[] parseConditionParts(String whereClause) {
        String text = whereClause.trim();
        if (text.isEmpty()) {
            throw new SqlException("После WHERE нужно условие, например id = 1");
        }

        int operatorIndex = -1;
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && (ch == '=' || ch == '!' || ch == '>' || ch == '<')) {
                operatorIndex = i;
                break;
            }
        }
        if (operatorIndex == -1) {
            throw new SqlException("В условии WHERE нет оператора. Можно использовать =, !=, >, <, >= или <=");
        }

        String twoChars = "";
        if (operatorIndex + 1 < text.length()) {
            twoChars = text.substring(operatorIndex, operatorIndex + 2);
        }
        String operator;
        if (twoChars.equals(">=") || twoChars.equals("<=") || twoChars.equals("!=")) {
            operator = twoChars;
        } else if (text.charAt(operatorIndex) == '!') {
            throw new SqlException("После ! должно идти =, то есть !=");
        } else {
            operator = String.valueOf(text.charAt(operatorIndex));
        }

        String column = text.substring(0, operatorIndex).trim();
        String value = text.substring(operatorIndex + operator.length()).trim();
        checkName(column, "колонки");
        if (value.isEmpty()) {
            throw new SqlException("В WHERE не хватает значения после " + operator);
        }
        char first = value.charAt(0);
        if (first == '=' || first == '<' || first == '>' || first == '!') {
            throw new SqlException("Непонятный оператор в WHERE. Можно использовать =, !=, >, <, >= или <=");
        }
        checkValue(value);
        boolean quoted = value.startsWith("'");
        if (!quoted && value.contains(" ")) {
            throw new SqlException("В WHERE можно только одно простое условие, AND и OR не поддерживаются");
        }
        return new String[]{column, operator, value};
    }

    /**
     * Делит текст по запятым, которые стоят вне кавычек.
     */
    private static List<String> splitByComma(String text) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (inQuotes) {
            throw new SqlException("Кавычка открыта, но не закрыта");
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    /**
     * Достаёт из VALUES (1, 'a'), (2, 'b') содержимое каждой пары скобок.
     */
    private static List<String> splitValueGroups(String text) {
        List<String> groups = new ArrayList<>();
        boolean inQuotes = false;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes) {
                continue;
            }
            if (ch == '(') {
                if (depth == 0) {
                    start = i + 1;
                }
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth < 0) {
                    throw new SqlException("В VALUES лишняя закрывающая скобка");
                }
                if (depth == 0) {
                    groups.add(text.substring(start, i).trim());
                }
            } else if (depth == 0 && ch != ',' && !Character.isWhitespace(ch)) {
                throw new SqlException("Значения в VALUES нужно писать в скобках: VALUES (1, 'BMW')");
            }
        }
        if (inQuotes) {
            throw new SqlException("Кавычка открыта, но не закрыта");
        }
        if (depth != 0) {
            throw new SqlException("В VALUES не закрыта скобка");
        }
        if (groups.isEmpty()) {
            throw new SqlException("После VALUES нужны значения в скобках: VALUES (1, 'BMW')");
        }
        return groups;
    }

    /**
     * Ищет ключевое слово (FROM, WHERE, ...) как отдельное слово вне кавычек, регистр не важен.
     *
     * @return позиция слова или -1, если его нет
     */
    private static int findKeyword(String text, String keyword) {
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes || !text.regionMatches(true, i, keyword, 0, keyword.length())) {
                continue;
            }
            boolean startOk = i == 0 || Character.isWhitespace(text.charAt(i - 1));
            int end = i + keyword.length();
            boolean endOk = end == text.length() || !isNameChar(text.charAt(end));
            if (startOk && endOk) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    /**
     * Проверяет, что имя таблицы, колонки или индекса состоит из латиницы, цифр и _.
     */
    private static void checkName(String name, String what) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new SqlException("Некорректное имя " + what + ": '" + name + "'. Можно латиницу, цифры и _");
        }
    }

    /**
     * Проверяет одно значение: оно не пустое и кавычки у строки закрыты.
     */
    private static void checkValue(String value) {
        if (value.isEmpty()) {
            throw new SqlException("Пропущено значение - между запятыми пусто");
        }
        boolean opens = value.startsWith("'");
        boolean closes = value.endsWith("'") && value.length() >= 2;
        if (opens != closes) {
            throw new SqlException("Кавычка у значения " + value + " не закрыта");
        }
    }
}
