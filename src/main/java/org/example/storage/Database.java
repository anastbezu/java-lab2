package org.example.storage;

import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.model.WhereCondition;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * База данных: хранит все таблицы и индексы, все изменения идут через неё.
 * При запуске читает таблицы (.db) и индексы (.idx) из папки с данными.
 * При INSERT, UPDATE и DELETE проверяет внешние ключи, обновляет индексы
 * и сразу сохраняет таблицу на диск, чтобы ничего не потерялось, если программу закроют.
 */
public class Database {
    private final File dataDir;
    private final TableRepository repository;
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final Map<String, TreeIndex> indexes = new LinkedHashMap<>();
    private final List<String> loadErrors = new ArrayList<>();

    /**
     * Открывает базу в указанной папке и загружает из неё таблицы и индексы.
     */
    public Database(File dataDir) {
        this.dataDir = dataDir;
        this.repository = new FileTableRepository(dataDir);
        loadTables();
        loadIndexes();
    }

    /**
     * Создаёт таблицу (CREATE TABLE). Внешний ключ должен ссылаться на первичный ключ
     * уже существующей таблицы и быть того же типа.
     *
     * @throws DatabaseException если таблица уже есть или внешний ключ задан неправильно
     */
    public Table createTable(TableSchema schema) throws DatabaseException {
        String tableName = schema.getTableName();
        if (hasTable(tableName)) {
            throw new DatabaseException("Таблица '" + tableName + "' уже есть, выберите другое имя");
        }
        for (Column col : schema.getColumns()) {
            if (col.isForeignKey()) {
                checkForeignKeyDefinition(col);
            }
        }
        Table table = new Table(schema);
        tables.put(tableName.toLowerCase(), table);
        save(table);
        return table;
    }

    /**
     * Удаляет таблицу (DROP TABLE) вместе с её индексами и файлами.
     *
     * @throws DatabaseException если таблицы нет или на неё ссылается другая таблица
     */
    public void dropTable(String tableName) throws DatabaseException {
        Table table = getTable(tableName);
        for (Table other : tables.values()) {
            if (other == table) {
                continue;
            }
            for (Column col : other.getSchema().getColumns()) {
                boolean refersToTable = col.isForeignKey() && col.getForeignKeyTable().equalsIgnoreCase(table.getName());
                if (refersToTable) {
                    throw new DatabaseException("Таблицу '" + table.getName()
                            + "' удалить не получится: на неё ссылается таблица '" + other.getName() + "'");
                }
            }
        }
        for (String indexName : table.getIndexes().keySet()) {
            indexes.remove(indexName);
            File indexFile = getIndexFile(indexName);
            indexFile.delete();
        }
        tables.remove(table.getName().toLowerCase());
        repository.delete(table.getName());
    }

    /**
     * Возвращает таблицу по имени, регистр не важен.
     * Читать таблицу можно напрямую, а менять - только через методы Database.
     *
     * @throws DatabaseException если таблицы нет
     */
    public Table getTable(String tableName) throws DatabaseException {
        String key = tableName.trim().toLowerCase();
        Table table = tables.get(key);
        if (table == null) {
            throw new DatabaseException("Таблицы '" + tableName + "' нет - проверьте имя");
        }
        return table;
    }

    /**
     * Есть ли таблица с таким именем.
     */
    public boolean hasTable(String tableName) {
        String key = tableName.trim().toLowerCase();
        return tables.containsKey(key);
    }

    /**
     * Вставляет одну строку (INSERT).
     */
    public void insertInto(String tableName, Row row) throws DatabaseException {
        List<Row> rows = new ArrayList<>();
        rows.add(row);
        insertAll(tableName, rows);
    }

    /**
     * Вставляет несколько строк (INSERT ... VALUES (...), (...)).
     * Если хоть одна строка не подходит, не вставляется ни одна.
     * Таблица сохраняется на диск один раз, поэтому это быстрее, чем insertInto в цикле.
     *
     * @return сколько строк вставлено
     * @throws DatabaseException если первичный ключ повторяется, тип не подходит или нет записи для внешнего ключа
     */
    public int insertAll(String tableName, List<Row> rows) throws DatabaseException {
        Table table = getTable(tableName);
        for (Row row : rows) {
            for (Column col : table.getSchema().getColumns()) {
                if (col.isForeignKey()) {
                    Object value = row.get(col.getName());
                    checkParentExists(col, value);
                }
            }
        }
        table.insertAll(rows);

        String pk = table.getSchema().getPrimaryKeyColumnName();
        for (Row row : rows) {
            Object key = row.get(pk);
            for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
                TreeIndex treeIndex = indexes.get(index.getKey());
                Object columnValue = row.get(index.getValue());
                treeIndex.put(columnValue, key);
            }
        }
        save(table);
        return rows.size();
    }

    /**
     * Обновляет строки, подходящие под условие (UPDATE ... SET ... WHERE ...).
     *
     * @param condition условие WHERE, null - все строки
     * @param newValues колонка -> новое значение
     * @return сколько строк изменено
     * @throws DatabaseException если тип не подходит, меняют первичный ключ или нет записи для внешнего ключа
     */
    public int update(String tableName, WhereCondition condition, Map<String, Object> newValues)
            throws DatabaseException {
        Table table = getTable(tableName);
        if (condition != null) {
            condition.check(table.getSchema());
        }
        for (Map.Entry<String, Object> value : newValues.entrySet()) {
            Column col = table.getSchema().getColumn(value.getKey());
            if (col != null && col.isForeignKey() && col.getType().isCompatible(value.getValue())) {
                checkParentExists(col, value.getValue());
            }
        }

        List<Row> oldRows = table.select(null, condition);
        int count = table.update(condition, newValues);

        String pk = table.getSchema().getPrimaryKeyColumnName();
        for (Row oldRow : oldRows) {
            Object key = oldRow.get(pk);
            Row newRow = table.getById(key);
            for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
                TreeIndex treeIndex = indexes.get(index.getKey());
                String columnName = index.getValue();
                treeIndex.remove(oldRow.get(columnName), key);
                treeIndex.put(newRow.get(columnName), key);
            }
        }
        save(table);
        return count;
    }

    /**
     * Удаляет строки, подходящие под условие (DELETE FROM ... WHERE ...).
     * Строку, на которую ссылается другая таблица, удалить нельзя.
     *
     * @param condition условие WHERE, null - все строки
     * @return сколько строк удалено
     * @throws DatabaseException если на удаляемую строку есть ссылка
     */
    public int delete(String tableName, WhereCondition condition) throws DatabaseException {
        Table table = getTable(tableName);
        if (condition != null) {
            condition.check(table.getSchema());
        }
        String pk = table.getSchema().getPrimaryKeyColumnName();
        List<Row> rowsToDelete = table.select(null, condition);

        Set<Object> keys = new HashSet<>();
        for (Row row : rowsToDelete) {
            keys.add(row.get(pk));
        }
        checkNotReferenced(table, keys);

        int count = table.delete(condition);
        for (Row row : rowsToDelete) {
            Object key = row.get(pk);
            for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
                TreeIndex treeIndex = indexes.get(index.getKey());
                Object columnValue = row.get(index.getValue());
                treeIndex.remove(columnValue, key);
            }
        }
        save(table);
        return count;
    }

    /**
     * Выборка по условию WHERE (SELECT).
     * Если по колонке из условия есть индекс, для всех операторов кроме != строки ищутся через него,
     * в остальных случаях просматривается вся таблица.
     *
     * @param columns   нужные колонки, null или ["*"] - все
     * @param condition условие WHERE, null - все строки
     * @throws DatabaseException если нет таблицы или колонки или условие не подходит к таблице
     */
    public List<Row> select(String tableName, List<String> columns, WhereCondition condition)
            throws DatabaseException {
        Table table = getTable(tableName);
        if (condition == null) {
            return table.select(columns, null);
        }
        condition.check(table.getSchema());
        Column column = table.getSchema().getColumn(condition.getColumn());
        Object value = column.getType().parse(condition.getValue());
        String operator = condition.getOperator();

        if (operator.equals("=")) {
            return selectEquals(tableName, column.getName(), value, columns);
        }
        boolean isRange = !operator.equals("!=");
        if (isRange && findIndex(tableName, column.getName()) != null) {
            return selectByIndex(tableName, column.getName(), operator, value, columns);
        }
        return table.select(columns, condition);
    }

    /**
     * Выборка по условию WHERE column = value.
     * Если по колонке есть индекс, строки ищутся через него, иначе просматривается вся таблица.
     *
     * @param value   значение нужного типа, то есть уже после DataType.parse
     * @param columns нужные колонки, null или ["*"] - все
     * @throws DatabaseException если нет таблицы или колонки или тип значения не подходит
     */
    public List<Row> selectEquals(String tableName, String columnName, Object value, List<String> columns)
            throws DatabaseException {
        if (findIndex(tableName, columnName) != null) {
            return selectByIndex(tableName, columnName, "=", value, columns);
        }
        Table table = getTable(tableName);
        checkValue(table, columnName, value);
        return table.selectEquals(columnName, value, columns);
    }

    /**
     * Выборка через индекс для условий =, &gt;, &lt;, &gt;= и &lt;=.
     *
     * @param op      оператор сравнения
     * @param value   значение нужного типа, то есть уже после DataType.parse
     * @param columns нужные колонки, null или ["*"] - все
     * @throws DatabaseException если по колонке нет индекса, тип значения не подходит или оператор не поддерживается
     */
    @SuppressWarnings("rawtypes")
    public List<Row> selectByIndex(String tableName, String columnName, String op, Object value,
                                   List<String> columns) throws DatabaseException {
        Table table = getTable(tableName);
        checkValue(table, columnName, value);
        TreeIndex index = findIndex(tableName, columnName);
        if (index == null) {
            throw new DatabaseException("По колонке '" + columnName + "' нет индекса, сначала создайте его");
        }
        table.checkColumnsExist(columns);

        List<Object> keys;
        switch (op) {
            case "=":
                keys = index.get(value);
                break;
            case ">":
                keys = index.greaterThan((Comparable) value);
                break;
            case "<":
                keys = index.lessThan((Comparable) value);
                break;
            case ">=":
                keys = index.greaterOrEqual((Comparable) value);
                break;
            case "<=":
                keys = index.lessOrEqual((Comparable) value);
                break;
            default:
                throw new DatabaseException("С оператором '" + op + "' индекс не работает");
        }

        List<Row> result = new ArrayList<>();
        for (Object key : keys) {
            Row row = table.getById(key);
            if (row != null) {
                Row projected = table.project(row, columns);
                result.add(projected);
            }
        }
        return result;
    }

    /**
     * Создаёт индекс (CREATE INDEX name ON table (column)), заполняет его
     * данными таблицы и сохраняет в файл имя.idx.
     *
     * @throws DatabaseException если имя индекса занято, нет таблицы или колонки или по колонке уже есть индекс
     */
    public void createIndex(String indexName, String tableName, String columnName) throws DatabaseException {
        String name = indexName.trim().toLowerCase();
        if (indexes.containsKey(name)) {
            throw new DatabaseException("Индекс '" + indexName + "' уже есть, выберите другое имя");
        }
        Table table = getTable(tableName);
        Column column = table.getSchema().getColumn(columnName);
        if (column == null) {
            throw new DatabaseException("В таблице '" + table.getName() + "' нет колонки '" + columnName + "'");
        }
        if (findIndex(tableName, columnName) != null) {
            throw new DatabaseException("По колонке '" + column.getName() + "' индекс уже построен");
        }

        TreeIndex index = buildIndex(table, column.getName());
        indexes.put(name, index);
        table.getIndexes().put(name, column.getName());
        save(table);
    }

    /**
     * Ищет индекс по таблице и колонке.
     *
     * @return индекс или null, если по колонке индекса нет
     */
    public TreeIndex findIndex(String tableName, String columnName) throws DatabaseException {
        Table table = getTable(tableName);
        String wanted = columnName.trim();
        for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
            if (index.getValue().equalsIgnoreCase(wanted)) {
                return indexes.get(index.getKey());
            }
        }
        return null;
    }

    /**
     * Есть ли индекс по колонке.
     */
    public boolean hasIndex(String tableName, String columnName) throws DatabaseException {
        return findIndex(tableName, columnName) != null;
    }

    /**
     * Возвращает ошибки, которые случились при загрузке с диска, например повреждённый файл таблицы.
     * Сама база ничего не печатает, показать их пользователю должна консоль.
     *
     * @return список сообщений, пустой, если всё загрузилось
     */
    public List<String> getLoadErrors() {
        return new ArrayList<>(loadErrors);
    }

    /**
     * Сохраняет таблицу и все её индексы на диск.
     */
    private void save(Table table) throws DatabaseException {
        repository.save(table);
        for (String indexName : table.getIndexes().keySet()) {
            TreeIndex index = indexes.get(indexName);
            String path = getIndexFile(indexName).getPath();
            try {
                index.save(path);
            } catch (Exception e) {
                throw new DatabaseException("Не удалось сохранить индекс '" + indexName + "' на диск: " + e.getMessage());
            }
        }
    }

    private void loadTables() {
        for (String name : repository.listTableNames()) {
            try {
                Table table = repository.load(name);
                tables.put(name.toLowerCase(), table);
            } catch (DatabaseException e) {
                loadErrors.add(e.getMessage());
            }
        }
    }

    /**
     * Загружает индексы из файлов .idx. Если файл повреждён или пропал,
     * индекс строится заново по данным таблицы и сразу сохраняется.
     */
    private void loadIndexes() {
        for (Table table : tables.values()) {
            for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
                String indexName = index.getKey();
                String path = getIndexFile(indexName).getPath();
                try {
                    TreeIndex loaded = TreeIndex.load(path);
                    indexes.put(indexName, loaded);
                } catch (Exception e) {
                    TreeIndex rebuilt = buildIndex(table, index.getValue());
                    indexes.put(indexName, rebuilt);
                    try {
                        rebuilt.save(path);
                    } catch (Exception saveError) {
                        loadErrors.add("Индекс '" + indexName + "' пересобран, но сохранить его не вышло: "
                                + saveError.getMessage());
                    }
                }
            }
        }
    }

    private TreeIndex buildIndex(Table table, String columnName) {
        String pk = table.getSchema().getPrimaryKeyColumnName();
        TreeIndex index = new TreeIndex();
        for (Row row : table) {
            Object columnValue = row.get(columnName);
            Object key = row.get(pk);
            index.put(columnValue, key);
        }
        return index;
    }

    private File getIndexFile(String indexName) {
        return new File(dataDir, indexName + ".idx");
    }

    /**
     * Проверяет, что колонка есть в таблице и значение подходит к её типу.
     */
    private void checkValue(Table table, String columnName, Object value) throws DatabaseException {
        Column column = table.getSchema().getColumn(columnName);
        if (column == null) {
            throw new DatabaseException("В таблице '" + table.getName() + "' нет колонки '" + columnName + "'");
        }
        if (!column.getType().isCompatible(value)) {
            throw new DatabaseException("Значение '" + value + "' не подходит к колонке типа " + column.getType());
        }
    }

    /**
     * Проверяет при CREATE TABLE, что внешний ключ ссылается на первичный ключ существующей таблицы того же типа.
     */
    private void checkForeignKeyDefinition(Column col) throws DatabaseException {
        String parentName = col.getForeignKeyTable();
        if (!hasTable(parentName)) {
            throw new DatabaseException("Внешний ключ '" + col.getName() + "' ссылается на таблицу '" + parentName
                    + "', а её ещё нет - создайте её первой");
        }
        Column parentPk = getTable(parentName).getSchema().getPrimaryKeyColumn();
        boolean sameName = parentPk.getName().equalsIgnoreCase(col.getForeignKeyColumn());
        boolean sameType = parentPk.getType() == col.getType();
        if (!sameName || !sameType) {
            throw new DatabaseException("Внешний ключ '" + col.getName() + "' должен ссылаться на первичный ключ '"
                    + parentName + "." + parentPk.getName() + "' типа " + parentPk.getType());
        }
    }

    /**
     * Проверяет, что в родительской таблице есть строка с таким первичным ключом.
     */
    private void checkParentExists(Column fkColumn, Object value) throws DatabaseException {
        if (value == null) {
            return;
        }
        String parentName = fkColumn.getForeignKeyTable();
        Table parent = getTable(parentName);
        if (!parent.containsKey(value)) {
            throw new DatabaseException("В таблице '" + parentName + "' нет записи с "
                    + fkColumn.getForeignKeyColumn() + " = " + value + ", сначала добавьте её");
        }
    }

    /**
     * Проверяет, что на удаляемые строки не ссылается ни одна другая таблица.
     */
    private void checkNotReferenced(Table parent, Set<Object> keys) throws DatabaseException {
        for (Table child : tables.values()) {
            for (Column col : child.getSchema().getColumns()) {
                boolean refersToParent = col.isForeignKey() && col.getForeignKeyTable().equalsIgnoreCase(parent.getName());
                if (!refersToParent) {
                    continue;
                }
                for (Row row : child) {
                    Object childValue = row.get(col.getName());
                    if (keys.contains(childValue)) {
                        throw new DatabaseException("Эту запись из '" + parent.getName()
                                + "' удалить нельзя: на неё ещё ссылается таблица '" + child.getName() + "'");
                    }
                }
            }
        }
    }
}
