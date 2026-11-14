package org.example;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.Row;
import org.example.model.TableSchema;
/**
 * Разбирает SQL-запросы и создаёт соответствующие команды
 * для их последующего выполнения.
 *
 * Поддерживает команды SELECT, INSERT, UPDATE, DELETE,
 * CREATE INDEX, CREATE TABLE и DROP TABLE.
 */
public class SqlParser {
    /**
     * Определяет тип SQL-запроса и передаёт его
     * соответствующему методу разбора.
     *
     * @param sql SQL-запрос пользователя
     * @return объект команды для выполнения запроса
     */
    public QueryCommand parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlException("SQL-запрос не может быть пустым");
        }
        sql = sql.trim().replaceAll(";+$", "");
        if (sql.isBlank()) {
            throw new SqlException("SQL-запрос не может быть пустым");
        }
        String upper = sql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("SELECT ")) {
            return parseSelect(sql);
        }
        if (upper.startsWith("INSERT INTO ")) {
            return parseInsert(sql);
        }
        if (upper.startsWith("UPDATE ")) {
            return parseUpdate(sql);
        }
        if (upper.startsWith("DELETE FROM ")) {
            return parseDelete(sql);
        }
        if (upper.startsWith("CREATE INDEX ")) {
            return parseCreateIndex(sql);
        }
        if (upper.startsWith("CREATE TABLE ")) {
            return parseCreateTable(sql);
        }
        if (upper.startsWith("DROP TABLE ")) {
            return parseDropTable(sql);
        }
        throw new SqlException("Неизвестная команда!");
    }
    /**
     * Разбирает SELECT-запрос, извлекает название таблицы,
     * выбранные столбцы и условие WHERE.
     *
     * @param sql SQL-запрос SELECT
     * @return команда SelectCommand
     */
    private QueryCommand parseSelect(String sql) {
        String text = sql.trim();
        if (!text.regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new SqlException("Ожидалось SELECT");
        }
        int fromIndex = indexOfKeywordOutsideQuotes(text, "FROM", 6);
        if (fromIndex == -1) {
            throw new SqlException("Отсутствует FROM");
        }
        if (indexOfKeywordOutsideQuotes(text, "FROM", fromIndex + 4) != -1) {
            throw new SqlException("Более одного FROM не может быть в запросе");
        }
        int whereIndex = indexOfKeywordOutsideQuotes(text, "WHERE", fromIndex + 4);
        if (whereIndex != -1 && indexOfKeywordOutsideQuotes(text, "WHERE", whereIndex + 5) != -1) {
            throw new SqlException("Более одного WHERE не может быть в запросе");
        }
        String columnsText = text.substring(6, fromIndex).trim();
        String tableText = whereIndex == -1 ? text.substring(fromIndex + 4).trim() : text.substring(fromIndex + 4, whereIndex).trim();
        if (columnsText.isEmpty()) {
            throw new SqlException("Столбцы не указаны");
        }
        if (tableText.isEmpty()) {
            throw new SqlException("Не введено имя таблицы");
        }
        if (tableText.split("\\s+").length != 1) {
            throw new SqlException("Введено что-то после названия таблицы");
        }
        String tableName = tableText;
        List<String> parsedColumns = splitByCommaOutsideQuotes(columnsText);
        String[] columns = new String[parsedColumns.size()];
        HashSet<String> uniqueColumns = new HashSet<>();
        for (int i = 0; i < parsedColumns.size(); i++) {
            columns[i] = parsedColumns.get(i).trim();
            if (columns[i].isEmpty()) {
                throw new SqlException("Есть пустые столбцы");
            }
            if (!uniqueColumns.add(columns[i].toLowerCase(Locale.ROOT))) {
                throw new SqlException("Есть повторяющиеся столбцы");
            }
        }
        if (Arrays.asList(columns).contains("*") && columns.length > 1) {
            throw new SqlException("Звездочка может быть единственным столбцом");
        }
        String conditionText = null;
        Predicate<Row> condition = null;
        if (whereIndex != -1) {
            conditionText = text.substring(whereIndex + 5).trim();
            validateWhere(conditionText);
            condition = parseWhere(conditionText);
        }
        return new SelectCommand(tableName, columns, condition, conditionText);
    }
    /**
     * Разбирает INSERT-запрос и извлекает название таблицы,
     * значения и, при наличии, названия столбцов.
     * Поддерживает вставку нескольких записей.
     *
     * @param sql SQL-запрос INSERT
     * @return команда InsertCommand
     */
    private QueryCommand parseInsert(String sql) {
        Pattern pattern = Pattern.compile("(?is)^INSERT\\s+INTO\\s+([A-Za-z_]\\w*)\\s+(.*)$");
        Matcher matcher = pattern.matcher(sql.trim());
        if (!matcher.matches()) {
            throw new SqlException("Неверный формат INSERT");
        }
        String tableName = matcher.group(1);
        String rest = matcher.group(2).trim();
        String[] columns = null;
        if (rest.startsWith("(")) {
            int closeIndex = rest.indexOf(")");
            if (closeIndex == -1) {
                throw new SqlException("Не закрыты скобки колонок");
            }
            String columnsText = rest.substring(1, closeIndex);
            List<String> parsedColumns = splitByCommaOutsideQuotes(columnsText);
            columns = new String[parsedColumns.size()];
            HashSet<String> uniqueColumns = new HashSet<>();
            for (int i = 0; i < columns.length; i++) {
                columns[i] = parsedColumns.get(i).trim();
                if (columns[i].isEmpty()) {
                    throw new SqlException("Пустое имя столбца");
                }
                if (!uniqueColumns.add(columns[i].toLowerCase(Locale.ROOT))) {
                    throw new SqlException("Повторяющиеся столбцы");
                }
            }
            rest = rest.substring(closeIndex + 1).trim();
        }
        Matcher valuesMatcher = Pattern.compile("(?is)^VALUES\\b\\s*(.*)$").matcher(rest);
        if (!valuesMatcher.matches()) {
            throw new SqlException("Ожидалось VALUES");
        }
        String valuesText = valuesMatcher.group(1).trim();
        List<String> valueGroups = splitValueGroups(valuesText);
        List<String[]> rowsValues = new ArrayList<>();
        for (String group : valueGroups) {
            List<String> parsedValues = splitByCommaOutsideQuotes(group);
            String[] values = new String[parsedValues.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = normalizeSqlValue(parsedValues.get(i));
            }
            if (columns != null && columns.length != values.length) {
                throw new SqlException("Количество колонок не совпадает с количеством значений");
            }
            rowsValues.add(values);
        }
        return new InsertCommand(tableName, rowsValues, columns);
    }
    /**
     * Разделяет группы значений INSERT, учитывая скобки и кавычки.
     *
     * @param text текст со значениями INSERT
     * @return список групп значений
     */
    private List<String> splitValueGroups(String text) {
        List<String> groups = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= text.length() || text.charAt(i) != '(') {
                throw new SqlException("Ожидалась открывающая скобка");
            }
            int start = ++i;
            int depth = 1;
            boolean inQuotes = false;
            while (i < text.length() && depth > 0) {
                char ch = text.charAt(i);
                if (ch == '\'') {
                    if (inQuotes && i + 1 < text.length()
                            && text.charAt(i + 1) == '\'') {
                        i += 2;
                        continue;
                    }
                    inQuotes = !inQuotes;
                }
                else if (!inQuotes) {
                    if (ch == '(') {
                        depth++;
                    }
                    else if (ch == ')') {
                        depth--;
                    }
                }
                i++;
            }
            if (depth != 0 || inQuotes) {
                throw new SqlException("Неправильные скобки или кавычки");
            }
            groups.add(text.substring(start, i - 1).trim());
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i < text.length()) {
                if (text.charAt(i) != ',') {
                    throw new SqlException("Ожидалась запятая между записями");
                }
                i++;
                if (i >= text.length()) {
                    throw new SqlException("После запятой отсутствует запись");
                }
            }
        }
        if (groups.isEmpty()) {
            throw new SqlException("Не указаны записи для INSERT");
        }
        return groups;
    }
    /**
     * Разделяет строку по запятым, не затрагивая запятые внутри кавычек.
     *
     * @param text исходный текст
     * @return список частей строки
     */
    private List<String> splitByCommaOutsideQuotes(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    current.append("''");
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                current.append(ch);
            }
            else if (ch == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            else {
                current.append(ch);
            }
        }
        if (inQuotes) {
            throw new SqlException("Незакрытые кавычки");
        }
        result.add(current.toString().trim());
        return result;
    }
    /**
     * Удаляет внешние кавычки из SQL-значения
     * и обрабатывает экранированные одинарные кавычки.
     *
     * @param value исходное значение
     * @return нормализованное значение
     */
    private static String normalizeSqlValue(String value) {
        String result = value.trim();
        if (result.isEmpty()) {
            throw new SqlException("Пустое значение");
        }
        if (result.startsWith("'") && result.endsWith("'") && result.length() >= 2) {
            return result.substring(1, result.length() - 1).replace("''", "'");
        }
        if (result.startsWith("'") || result.endsWith("'")) {
            throw new SqlException("Незакрытые кавычки");
        }
        return result;
    }
    /**
     * Разбирает UPDATE-запрос, извлекает таблицу,
     * изменяемый столбец, новое значение и условие WHERE.
     *
     * @param sql SQL-запрос UPDATE
     * @return команда UpdateCommand
     */
    private QueryCommand parseUpdate(String sql) {
        Matcher matcher = Pattern.compile("(?is)^UPDATE\\s+([A-Za-z_]\\w*)\\s+SET\\s+(.*)$").matcher(sql.trim());
        if (!matcher.matches()) {
            throw new SqlException("Неверный формат UPDATE");
        }
        String tableName = matcher.group(1);
        String rest = matcher.group(2).trim();
        int whereIndex = indexOfKeywordOutsideQuotes(rest, "WHERE", 0);
        if (whereIndex == -1) {
            throw new SqlException("Отсутствует WHERE");
        }
        if (indexOfKeywordOutsideQuotes(rest, "WHERE", whereIndex + 5) != -1) {
            throw new SqlException("Более одного WHERE не может быть в запросе");
        }
        String assignment = rest.substring(0, whereIndex).trim();
        String conditionText = rest.substring(whereIndex + 5).trim();
        int equalsIndex = indexOfOperatorOutsideQuotes(assignment, "=");
        if (equalsIndex == -1) {
            throw new SqlException("Некорректное присваивание в SET");
        }
        String columnName = assignment.substring(0, equalsIndex).trim();
        String newValue = assignment.substring(equalsIndex + 1).trim();
        if (columnName.isEmpty() || newValue.isEmpty()) {
            throw new SqlException("Некорректное присваивание в SET");
        }
        newValue = normalizeSqlValue(newValue);
        validateWhere(conditionText);
        Predicate<Row> condition = parseWhere(conditionText);
        String[] conditionParts = parseConditionParts(conditionText);
        return new UpdateCommand(tableName, columnName, newValue, condition, conditionParts[0], conditionParts[1]);
    }
    /**
     * Разбирает DELETE-запрос, извлекает название таблицы
     * и условие WHERE.
     *
     * @param sql SQL-запрос DELETE
     * @return команда DeleteCommand
     */
    private QueryCommand parseDelete(String sql) {
        Matcher matcher = Pattern.compile("(?is)^DELETE\\s+FROM\\s+([A-Za-z_]\\w*)\\s+WHERE\\s+(.+)$").matcher(sql.trim());
        if (!matcher.matches()) {
            throw new SqlException("Неверный формат DELETE");
        }
        String tableName = matcher.group(1);
        String conditionText = matcher.group(2).trim();
        validateWhere(conditionText);
        Predicate<Row> condition = parseWhere(conditionText);
        String[] conditionParts = parseConditionParts(conditionText);
        return new DeleteCommand(tableName, condition, conditionParts[0], conditionParts[1]);
    }
    /**
     * Проверяет формат условия WHERE:
     * наличие столбца, оператора и значения.
     *
     * @param whereClause текст условия WHERE
     */
    private void validateWhere(String whereClause) {
        String[] condition = parseConditionParts(whereClause);
        if (!Arrays.asList(">", "<", "!=", "=").contains(condition[1])) {
            throw new SqlException("Неверный оператор WHERE");
        }
        if (condition[1].equals(">") || condition[1].equals("<")) {
            try {
                new BigDecimal(condition[2]);
            } catch (NumberFormatException e) {
                throw new SqlException("Для операторов > и < нужно числовое значение");
            }
        }
    }
    /**
     * Разбирает условие WHERE на имя столбца, оператор и значение.
     * Операторы внутри строковых значений игнорируются.
     *
     * @param whereClause текст условия WHERE
     * @return массив из имени столбца, оператора и значения
     */
    static String[] parseConditionParts(String whereClause) {
        if (whereClause == null || whereClause.isBlank()) {
            throw new SqlException("WHERE не указан");
        }
        String text = whereClause.trim();
        boolean inQuotes = false;
        int operatorIndex = -1;
        String operator = null;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                String currentOperator = null;
                if (ch == '!' && i + 1 < text.length() && text.charAt(i + 1) == '=') {
                    currentOperator = "!=";
                    i++;
                } else if (ch == '=' || ch == '>' || ch == '<') {
                    currentOperator = String.valueOf(ch);
                }
                if (currentOperator != null) {
                    if (operatorIndex != -1) {
                        throw new SqlException("В WHERE слишком много операторов");
                    }
                    operatorIndex = currentOperator.equals("!=") ? i - 1 : i;
                    operator = currentOperator;
                }
            }
        }
        if (inQuotes) {
            throw new SqlException("Незакрытые кавычки в WHERE");
        }
        if (operatorIndex == -1) {
            throw new SqlException("Неверное условие WHERE");
        }
        String column = text.substring(0, operatorIndex).trim();
        String value = text.substring(operatorIndex + operator.length()).trim();
        if (!column.matches("[A-Za-z_]\\w*") || value.isEmpty()) {
            throw new SqlException("Неверное условие WHERE");
        }
        return new String[]{column, operator, normalizeSqlValue(value)};
    }
    /**
     * Сравнивает два значения с учётом оператора WHERE.
     *
     * @param comparison результат сравнения значений
     * @param operator оператор сравнения
     * @return результат проверки условия
     */
    private static boolean compareValues(int comparison, String operator) {
        switch (operator) {
            case "=":
                return comparison == 0;
            case "!=":
                return comparison != 0;
            case ">":
                return comparison > 0;
            case "<":
                return comparison < 0;
            default:
                return false;
        }
    }
    /**
     * Преобразует текстовое условие WHERE в функцию проверки строк.
     *
     * @param whereClause текст условия WHERE
     * @return Predicate, проверяющий соответствие строки условию
     */
    public static Predicate<Row> parseWhere(String whereClause) {
        String[] parts = parseConditionParts(whereClause);
        String column = parts[0];
        String operator = parts[1];
        String value = parts[2];
        return row -> {
            Object cell = row.get(column);
            if (cell == null) {
                return false;
            }
            if (cell instanceof Number) {
                int comparison;
                try {
                    comparison = new BigDecimal(cell.toString()).compareTo(new BigDecimal(value));
                } catch (NumberFormatException e) {
                    throw new SqlException("Некорректное числовое значение: " + value);
                }
                return compareValues(comparison, operator);
            }
            if (cell instanceof Boolean) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new SqlException("Для Boolean нужно TRUE или FALSE");
                }
                if (operator.equals(">") || operator.equals("<")) {
                    throw new SqlException("Для Boolean нельзя использовать > и <");
                }
                boolean expected = Boolean.parseBoolean(value);
                return operator.equals("=") ? cell.equals(expected) : !cell.equals(expected);
            }
            if (operator.equals(">") || operator.equals("<")) {
                throw new SqlException("Операторы > и < нельзя использовать со строками");
            }
            int comparison = cell.toString().compareTo(value);
            return compareValues(comparison, operator);
        };
    }
    /**
     * Находит ключевое слово SQL вне одинарных кавычек.
     *
     * @param text исходный текст
     * @param keyword искомое ключевое слово
     * @param fromIndex позиция начала поиска
     * @return индекс начала ключевого слова или -1
     */
    private static int indexOfKeywordOutsideQuotes(String text, String keyword, int fromIndex) {
        boolean inQuotes = false;
        for (int i = 0; i <= text.length() - keyword.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (i < fromIndex || inQuotes) {
                continue;
            }
            if (text.regionMatches(true, i, keyword, 0, keyword.length())) {
                boolean leftBoundary = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)) && text.charAt(i - 1) != '_';
                int end = i + keyword.length();
                boolean rightBoundary = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end)) && text.charAt(end) != '_';
                if (leftBoundary && rightBoundary) {
                    return i;
                }
            }
        }
        return -1;
    }
    /**
     * Находит оператор вне одинарных кавычек.
     *
     * @param text исходный текст
     * @param operator искомый оператор
     * @return индекс оператора или -1
     */
    private static int indexOfOperatorOutsideQuotes(String text, String operator) {
        boolean inQuotes = false;
        for (int i = 0; i <= text.length() - operator.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && text.startsWith(operator, i)) {
                return i;
            }
        }
        return -1;
    }
    /**
     * Разбирает CREATE INDEX и извлекает имя индекса,
     * название таблицы и индексируемого столбца.
     *
     * @param sql SQL-запрос CREATE INDEX
     * @return команда CreateIndexCommand
     */
    private QueryCommand parseCreateIndex(String sql) {
        Pattern pattern = Pattern.compile("(?i)^CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)$");
        Matcher matcher = pattern.matcher(sql.trim());
        if (!matcher.matches()) {
            throw new SqlException("Неверный формат CREATE INDEX");
        }
        String indexName = matcher.group(1);
        String tableName = matcher.group(2);
        String columnName = matcher.group(3);
        return new CreateIndexCommand(indexName, tableName, columnName);
    }
    /**
     * Разбирает CREATE TABLE, извлекает название таблицы,
     * описания столбцов, их типы и информацию о ключах.
     *
     * @param sql SQL-запрос CREATE TABLE
     * @return команда CreateTableCommand
     */
    private QueryCommand parseCreateTable(String sql) {
        String query = sql.trim();
        int openIndex = query.indexOf("(");
        int closeIndex = query.lastIndexOf(")");
        if (openIndex == -1 || closeIndex == -1 || openIndex > closeIndex) {
            throw new SqlException("Неверный формат CREATE TABLE");
        }
        if (!query.substring(closeIndex + 1).trim().isEmpty()) {
            throw new SqlException("Лишний текст после CREATE TABLE");
        }
        String header = query.substring(0, openIndex).trim();
        String[] headerParts = header.split("\\s+");
        if (headerParts.length != 3 || !headerParts[0].equalsIgnoreCase("CREATE") || !headerParts[1].equalsIgnoreCase("TABLE")) {
            throw new SqlException("Неверное объявление CREATE TABLE");
        }
        String tableName = headerParts[2];
        if (!tableName.matches("[A-Za-z_]\\w*")) {
            throw new SqlException("Некорректное имя таблицы");
        }
        String columnsText = query.substring(openIndex + 1, closeIndex);
        List<String> columnDefinitions = splitByCommaOutsideQuotes(columnsText);
        List<Column> columns = new ArrayList<>();
        HashSet<String> uniqueColumns = new HashSet<>();
        for (String definition : columnDefinitions) {
            String[] parts = definition.trim().split("\\s+", 3);
            if (parts.length < 2) {
                throw new SqlException("Неверное описание столбца");
            }
            String columnName = parts[0];
            if (!columnName.matches("[A-Za-z_]\\w*")) {
                throw new SqlException("Некорректное имя столбца: " + columnName);
            }
            if (!uniqueColumns.add(columnName.toLowerCase(Locale.ROOT))) {
                throw new SqlException("Повторяющийся столбец: " + columnName);
            }
            DataType type;
            try {
                type = DataType.valueOf(parts[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new SqlException("Неизвестный тип данных: " + parts[1]);
            }
            boolean primaryKey = false;
            String foreignKeyTable = null;
            String foreignKeyColumn = null;
            String constraint = parts.length == 3 ? parts[2].trim() : "";
            if (constraint.isEmpty()) {

            } else if (constraint.equalsIgnoreCase("PRIMARY KEY")) {
                primaryKey = true;
            } else {
                Matcher referenceMatcher = Pattern.compile("(?i)^REFERENCES\\s+([A-Za-z_]\\w*)\\s*\\(\\s*([A-Za-z_]\\w*)\\s*\\)$").matcher(constraint);
                if (!referenceMatcher.matches()) {
                    throw new SqlException("Неверное описание столбца: " + definition);
                }
                foreignKeyTable = referenceMatcher.group(1);
                foreignKeyColumn = referenceMatcher.group(2);
            }
            if (foreignKeyTable != null) {
                columns.add(new Column(columnName, type, foreignKeyTable, foreignKeyColumn));
            } else {
                columns.add(new Column(columnName, type, primaryKey));
            }
        }
        if (columns.isEmpty()) {
            throw new SqlException("В таблице должен быть хотя бы один столбец");
        }
        TableSchema schema = new TableSchema(tableName, columns);
        return new CreateTableCommand(schema);
    }
    /**
     * Разбирает DROP TABLE и извлекает название удаляемой таблицы.
     *
     * @param sql SQL-запрос DROP TABLE
     * @return команда DropTableCommand
     */
    private QueryCommand parseDropTable(String sql) {
        Pattern pattern = Pattern.compile("(?i)^DROP\\s+TABLE\\s+(\\w+)$");
        Matcher matcher = pattern.matcher(sql.trim());
        if (!matcher.matches()) {
            throw new SqlException("Неверный формат DROP TABLE");
        }
        String tableName = matcher.group(1);
        return new DropTableCommand(tableName);
    }
}