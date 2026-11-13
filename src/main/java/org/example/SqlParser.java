package org.example;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.Row;
import org.example.model.TableSchema;
import java.util.ArrayList;
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
     * @throws Exception если запрос некорректен или неизвестен
     */
    public QueryCommand parse(String sql) throws Exception {
        sql = sql.trim().replaceAll(";+$", "");
        String upper = sql.toUpperCase();
        if (upper.startsWith("SELECT")) {
            return parseSelect(sql);
        }
        if (upper.startsWith("INSERT INTO")) {
            return parseInsert(sql);
        }
        if (upper.startsWith("UPDATE")) {
            return parseUpdate(sql);
        }
        if (upper.startsWith("DELETE FROM")) {
            return parseDelete(sql);
        }
        if (upper.startsWith("CREATE INDEX")) {
            return parseCreateIndex(sql);
        }
        if (upper.startsWith("CREATE TABLE")) {
            return parseCreateTable(sql);
        }
        if (upper.startsWith("DROP TABLE")) {
            return parseDropTable(sql);
        }
        throw new IllegalArgumentException("Неизвестная команда!");
    }
    /**
     * Разбирает SELECT-запрос, извлекает название таблицы,
     * выбранные столбцы и условие WHERE.
     *
     * @param sql SQL-запрос SELECT
     * @return команда SelectCommand
     * @throws Exception если запрос имеет неправильный формат
     */
    private QueryCommand parseSelect(String sql) throws Exception {
        String[] upperParts = sql.toUpperCase().split("\\s+");
        String[] parts = sql.trim().split("\\s+");
        int fromIndex = Arrays.asList(upperParts).indexOf("FROM");
        int whereIndex = Arrays.asList(upperParts).indexOf("WHERE");
        int selectIndex = Arrays.asList(upperParts).indexOf("SELECT");
        int endIndex = whereIndex != -1 ? whereIndex : parts.length;
        if (Arrays.asList(upperParts).lastIndexOf("SELECT") != selectIndex) {
            throw new IllegalArgumentException("Более одного SELECT не может быть в запросе");
        }
        if (selectIndex != 0) {
            throw new IllegalArgumentException("SELECT должен стоять в начале запроса");
        }
        if (fromIndex == -1) {
            throw new IllegalArgumentException("Отсутствует FROM");
        }
        if (Arrays.asList(upperParts).lastIndexOf("FROM") != fromIndex) {
            throw new IllegalArgumentException("Более одного FROM не может быть в запросе");
        }
        if (whereIndex != -1 && whereIndex <= fromIndex) {
            throw new IllegalArgumentException("WHERE не может стоять раньше FROM");
        }
        if (fromIndex + 1 >= endIndex) {
            throw new IllegalArgumentException("Не введено имя таблицы");
        }
        if (endIndex - fromIndex - 1 != 1) {
            throw new IllegalArgumentException("Введено что-то после названия таблицы");
        }
        if (Arrays.asList(upperParts).lastIndexOf("WHERE") != whereIndex) {
            throw new IllegalArgumentException("Более одного WHERE не может быть в запросе");
        }
        String tableName = parts[fromIndex + 1];
        String[] fields = Arrays.copyOfRange(parts, 1, fromIndex);
        if (fields.length == 0) {
            throw new IllegalArgumentException("Столбцы не указаны");
        }
        for (int i = 0; i < fields.length - 1; i++) {
            if (!fields[i].endsWith(",")) {
                throw new IllegalArgumentException("Пользователь не ввел запятую!");
            }
        }
        if (fields[fields.length - 1].endsWith(",")) {
            throw new IllegalArgumentException("В конце последнего столбца есть запятая");
        }
        String columnsText = String.join(" ", fields);
        String[] columns = columnsText.split(",");
        HashSet<String> uniqueColumns = new HashSet<>();
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columns[i].trim();
            if (columns[i].isEmpty()) {
                throw new IllegalArgumentException("Есть пустые столбцы");
            }
            if (!uniqueColumns.add(columns[i].toLowerCase())) {
                throw new IllegalArgumentException("Есть повторяющиеся столбцы");
            }
        }
        if (Arrays.asList(columns).contains("*") && columns.length > 1) {
            throw new IllegalArgumentException("Звездочка может быть единственным столбцом");
        }
        String condText = null;
        Predicate<Row> condition = null;
        if (whereIndex != -1) {
            String[] cond = Arrays.copyOfRange(parts, whereIndex + 1, parts.length);
            condText = String.join(" ", cond);
            validateWhere(condText);
            condition = parseWhere(condText);
        }
        return new SelectCommand(tableName, columns, condition, condText);
    }
    /**
     * Разбирает INSERT-запрос и извлекает название таблицы,
     * значения и, при наличии, названия столбцов.
     *
     * @param sql SQL-запрос INSERT
     * @return команда InsertCommand
     * @throws Exception если запрос имеет неправильный формат
     */
    private QueryCommand parseInsert(String sql) throws Exception {
        String[] parts = sql.trim().split("\\s+", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Неверный формат INSERT");
        }
        if (!(parts[0].equalsIgnoreCase("INSERT") && parts[1].equalsIgnoreCase("INTO"))) {
            throw new IllegalArgumentException("Некорректный ввод INSERT");
        }
        if (parts[2].isBlank()) {
            throw new IllegalArgumentException("Не указано имя таблицы");
        }
        String tableName = parts[2];
        String[] columns = null;
        String rest = parts[3].trim();

        if (rest.startsWith("(")) {
            int closeIndex = rest.indexOf(")");

            if (closeIndex == -1) {
                throw new IllegalArgumentException("Не закрыты скобки колонок");
            }

            String columnsText = rest.substring(1, closeIndex);
            columns = columnsText.split(",", -1);

            for (int i = 0; i < columns.length; i++) {
                columns[i] = columns[i].trim();
            }

            rest = rest.substring(closeIndex + 1).trim();
        }
        if (!rest.toUpperCase().matches("VALUES\\s*\\(.*")) {
            throw new IllegalArgumentException("Ожидалось VALUES");
        }

        String valuesText = rest.substring(6).trim();

        if (!valuesText.startsWith("(") || !valuesText.endsWith(")")) {
            throw new IllegalArgumentException("Неверный формат значений");
        }

        valuesText = valuesText.substring(1, valuesText.length() - 1);
        String[] values = valuesText.split(",", -1);
        if (columns != null && columns.length != values.length) {
            throw new IllegalArgumentException("Количество колонок не совпадает с количеством значений");
        }
        for (String value : values) {
            if (value.trim().isEmpty()) {
                throw new IllegalArgumentException("Пустое значение");
            }
        }
        if (columns != null) {
            if (columns.length == 0) {
                throw new IllegalArgumentException("Не указаны колонки");
            }
            for (String column : columns) {
                if (column == null || column.isBlank()) {
                    throw new IllegalArgumentException("Пустое имя колонки");
                }
            }
        }
        for (int i = 0; i < values.length ; i++) {
            values[i] = values[i].trim();
            if (values[i].startsWith("'") && values[i].endsWith("'")) {
                values[i] = values[i].substring(1, values[i].length()-1);
            }
            if (values[i].startsWith("'") || values[i].endsWith("'")) {
                throw new IllegalArgumentException("Незакрытые кавычки в значении");
            }
        }
        return new InsertCommand(tableName, values, columns);
    }
    /**
     * Разбирает UPDATE-запрос, извлекает таблицу,
     * изменяемый столбец, новое значение и условие WHERE.
     *
     * @param sql SQL-запрос UPDATE
     * @return команда UpdateCommand
     * @throws Exception если запрос имеет неправильный формат
     */
    private QueryCommand parseUpdate(String sql) throws Exception {
        String[] parts = sql.split("\\s+", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Неверный формат UPDATE");
        }
        if (!(parts[0].equalsIgnoreCase("UPDATE"))) {
            throw new IllegalArgumentException("Ожидалось UPDATE");
        }
        if (!(parts[2].equalsIgnoreCase("SET"))) {
            throw new IllegalArgumentException("Ожидалось SET");
        }
        String tableName = parts[1];
        String[] updateParts = parts[3].split("(?i)\\s+WHERE\\s+", 2);
        if (updateParts.length != 2 || updateParts[0].isBlank() || updateParts[1].isBlank()) {
            throw new IllegalArgumentException("Некорректный формат WHERE");
        }
        String[] assignmentParts = updateParts[0].split("=", 2);
        if (assignmentParts.length != 2 || assignmentParts[0].isBlank() ||  assignmentParts[1].isBlank()) {
            throw new IllegalArgumentException("Некорректное присваивание в SET");
        }
        String columnName = assignmentParts[0].trim();
        String newValue = assignmentParts[1].trim();
        String conditionText = updateParts[1].trim();
        validateWhere(conditionText);
        Predicate<Row> condition = parseWhere(conditionText);
        return new UpdateCommand(tableName, columnName, newValue, condition);
    }
    /**
     * Разбирает DELETE-запрос, извлекает название таблицы
     * и условие WHERE.
     *
     * @param sql SQL-запрос DELETE
     * @return команда DeleteCommand
     * @throws Exception если запрос имеет неправильный формат
     */
    private QueryCommand parseDelete(String sql) throws Exception {
        String[] parts = sql.trim().split("\\s+", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Неверный формат DELETE");
        }
        if (!(parts[0].equalsIgnoreCase("DELETE"))) {
            throw new IllegalArgumentException("Ожидалось DELETE");
        }
        if (!(parts[1].equalsIgnoreCase("FROM"))) {
            throw new IllegalArgumentException("Ожидалось FROM");
        }
        if (parts[2].isBlank()) {
            throw new IllegalArgumentException("Не указано имя таблицы");
        }
        String[] whereParts = parts[3].trim().split("\\s+", 2);
        if (whereParts.length != 2 || !whereParts[0].equalsIgnoreCase("WHERE")) {
            throw new IllegalArgumentException("Ожидалось WHERE и условие");
        }
        String conditionText = whereParts[1].trim();
        validateWhere(conditionText);
        Predicate <Row> condition = parseWhere(conditionText);
        String tableName = parts[2];
        return new DeleteCommand(tableName, condition);
    }
    /**
     * Проверяет формат условия WHERE:
     * наличие трёх частей, допустимость оператора
     * и числовое значение для операторов сравнения.
     *
     * @param whereClause текст условия WHERE
     * @throws IllegalArgumentException если условие составлено неправильно
     */
    private void validateWhere(String whereClause) {
        if (whereClause == null || whereClause.isBlank()) {
            throw new IllegalArgumentException("WHERE не указан");
        }
        String[] condition = whereClause.trim().split("\\s+");
        if (condition.length != 3) {
            throw new IllegalArgumentException("Неверное условие WHERE");
        }
        if (!(Arrays.asList(">", "<", "!=", "=").contains(condition[1]))) {
            throw new IllegalArgumentException("Неверное условие WHERE");
        }
        if (condition[1].equals(">") || condition[1].equals("<")) {
            try {
                Double.parseDouble(condition[2].replace("'", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Для операторов > и < нужно числовое значение"
                );
            }
        }
    }
    /**
     * Преобразует текстовое условие WHERE в функцию проверки строк.
     *
     * @param whereClause текст условия WHERE
     * @return Predicate, проверяющий соответствие строки условию
     */
    public static Predicate<Row> parseWhere(String whereClause) {
        String[] parts = whereClause.trim().split("\\s+");
        String col = parts[0];
        String op = parts[1];
        String rawVal = parts[2].replace("'", "");
        return row -> {
            Object cell = row.get(col);
            if (cell == null) return false;
            if (op.equals("=")) return cell.toString().equals(rawVal);
            if (op.equals("!=")) return !cell.toString().equals(rawVal);
            if (op.equals(">")) return Double.parseDouble(cell.toString()) > Double.parseDouble(rawVal);
            if (op.equals("<")) return Double.parseDouble(cell.toString()) < Double.parseDouble(rawVal);
            return false;
        };
    }
    /**
     * Разбирает CREATE INDEX и извлекает имя индекса,
     * название таблицы и индексируемого столбца.
     *
     * @param sql SQL-запрос CREATE INDEX
     * @return команда CreateIndexCommand
     */
    private QueryCommand parseCreateIndex (String sql) {
        Pattern pattern = Pattern.compile("(?i)^CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)$");
        Matcher matcher = pattern.matcher(sql.trim());
        if (!(matcher.matches())) {
            throw new IllegalArgumentException("Неверный формат CREATE INDEX");
        }
        String indexName = matcher.group(1);
        String tableName = matcher.group(2);
        String columnName = matcher.group(3);
        return new CreateIndexCommand(indexName, tableName, columnName);
    }
    /**
     * Разбирает CREATE TABLE, извлекает название таблицы,
     * описания столбцов, их типы и информацию о первичном ключе.
     *
     * @param sql SQL-запрос CREATE TABLE
     * @return команда CreateTableCommand
     */
    private QueryCommand parseCreateTable(String sql) {
        String query = sql.trim();
        int openIndex = query.indexOf("(");
        int closeIndex = query.lastIndexOf(")");
        if (openIndex == -1 || closeIndex == -1 || openIndex > closeIndex) {
            throw new IllegalArgumentException("Неверный формат CREATE TABLE");
        }
        if (!(query.substring(closeIndex + 1).trim().isEmpty())) {
            throw new IllegalArgumentException("Лишний текст после CREATE TABLE");
        }
        String header = query.substring(0, openIndex).trim();
        String[] headerParts = header.split("\\s+");
        if (headerParts.length != 3 || !(headerParts[0].equalsIgnoreCase("CREATE")) || !(headerParts[1].equalsIgnoreCase("TABLE"))) {
            throw new IllegalArgumentException("Неверное объявление CREATE TABLE");
        }
        String tableName = headerParts[2];
        if (!tableName.matches("[A-Za-z_]\\w*")) {
            throw new IllegalArgumentException("Некорректное имя таблицы");
        }
        String columnsText = query.substring(openIndex + 1, closeIndex);
        String[] columnDefinitions = columnsText.split(",", -1);
        List<Column> columns = new ArrayList<>();
        for (String definition : columnDefinitions) {
            String[] parts = definition.trim().split("\\s+");
            if (parts.length != 2 && parts.length != 4) {
                throw new IllegalArgumentException("Неверное описание столбца");
        }
            String columnName = parts[0];
            if (!columnName.matches("[A-Za-z_]\\w*")) {
                throw new IllegalArgumentException("Некорректное имя столбца: " + columnName);
            }
        boolean primaryKey = false;
            if (parts.length == 4) {
                if (!parts[2].equalsIgnoreCase("PRIMARY") ||
                        !parts[3].equalsIgnoreCase("KEY")) {
                    throw new IllegalArgumentException("Неверное объявление PRIMARY KEY");
                }
                primaryKey = true;
            }
            DataType type;
            try {
                type = DataType.valueOf(parts[1].toUpperCase());
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Неизвестный тип данных: " + parts[1]);
            }
            columns.add(new Column(columnName, type, primaryKey));
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
        if (!(matcher.matches())) {
            throw new IllegalArgumentException("Неверный формат DROP TABLE");
        }
        String tableName = matcher.group(1);
        return new DropTableCommand(tableName);
        }
    }



