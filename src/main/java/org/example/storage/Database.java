package org.example.storage;

import org.example.TreeIndex;
import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.Row;
import org.example.model.TableSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * База данных: хранит все таблицы и индексы и через неё выполняются все операции.
 * <ul>
 *     <li>при запуске загружает таблицы ({@code .db}) и индексы ({@code .idx}) из папки с данными;</li>
 *     <li>проверяет внешние ключи при INSERT, UPDATE и DELETE;</li>
 *     <li>обновляет индексы при каждом изменении данных;</li>
 *     <li>после каждого изменения сразу сохраняет таблицу и её индексы на диск,
 *     поэтому при внезапном закрытии программы ничего не теряется.</li>
 * </ul>
 */
public class Database {
    private final File dataDir;
    private final TableRepository repository;
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final Map<String, TreeIndex> indexes = new LinkedHashMap<>();

    /**
     * Открывает базу в папке {@code data}.
     */
    public Database() {
        this(new File("data"));
    }

    /**
     * Открывает базу в указанной папке и загружает из неё все таблицы и индексы.
     */
    public Database(File dataDir) {
        this.dataDir = dataDir;
        this.repository = new FileTableRepository(dataDir);
        loadTables();
        loadIndexes();
    }

    /**
     * Создаёт таблицу (CREATE TABLE). Внешний ключ должен ссылаться на первичный ключ
     * уже существующей таблицы и иметь тот же тип.
     *
     * @throws DatabaseException если таблица уже есть или внешний ключ задан неверно
     */
    public Table createTable(TableSchema schema) throws DatabaseException {
        if (hasTable(schema.getTableName())) {
            throw new DatabaseException("Таблица '" + schema.getTableName() + "' уже существует");
        }
        for (Column col : schema.getColumns()) {
            if (col.isForeignKey()) {
                checkForeignKeyDefinition(col);
            }
        }
        Table table = new Table(schema);
        tables.put(table.getName().toLowerCase(), table);
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
            for (Column col : other.getSchema().getColumns()) {
                if (other != table && col.isForeignKey() && col.getForeignKeyTable().equalsIgnoreCase(table.getName())) {
                    throw new DatabaseException("Нельзя удалить таблицу '" + table.getName()
                            + "': на неё ссылается таблица '" + other.getName() + "'");
                }
            }
        }
        for (String indexName : table.getIndexes().keySet()) {
            indexes.remove(indexName);
            getIndexFile(indexName).delete();
        }
        tables.remove(table.getName().toLowerCase());
        repository.delete(table.getName());
    }

    /**
     * Возвращает таблицу по имени (без учёта регистра).
     * Таблицу можно читать напрямую ({@code select}, {@code getById}, {@code getSchema}),
     * а изменять — только через методы {@code Database}.
     *
     * @throws DatabaseException если таблицы нет
     */
    public Table getTable(String tableName) throws DatabaseException {
        Table table = tables.get(tableName.trim().toLowerCase());
        if (table == null) {
            throw new DatabaseException("Таблица '" + tableName + "' не найдена");
        }
        return table;
    }

    public boolean hasTable(String tableName) {
        return tables.containsKey(tableName.trim().toLowerCase());
    }

    public List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
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
     * Если хотя бы одна строка не подходит, не вставляется ни одна.
     * Таблица сохраняется на диск один раз, поэтому для загрузки большого
     * количества строк (бенчмарк) этот метод намного быстрее, чем insertInto в цикле.
     *
     * @return сколько строк вставлено
     * @throws DatabaseException при дубликате первичного ключа, неверном типе или несуществующем внешнем ключе
     */
    public int insertAll(String tableName, List<Row> rows) throws DatabaseException {
        Table table = getTable(tableName);
        for (Row row : rows) {
            for (Column col : table.getSchema().getColumns()) {
                if (col.isForeignKey()) {
                    checkParentExists(col, row.get(col.getName()));
                }
            }
        }
        table.insertAll(rows);

        String pk = table.getSchema().getPrimaryKeyColumnName();
        for (Row row : rows) {
            for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
                indexes.get(index.getKey()).put(row.get(index.getValue()), row.get(pk));
            }
        }
        save(table);
        return rows.size();
    }

    /**
     * Обновляет строки, подходящие под условие (UPDATE ... SET ... WHERE ...).
     *
     * @param condition условие WHERE; null — все строки
     * @param newValues колонка -> новое значение
     * @return сколько строк изменено
     * @throws DatabaseException при неверном типе, попытке изменить первичный ключ или несуществующем внешнем ключе
     */
    public int update(String tableName, Predicate<Row> condition, Map<String, Object> newValues)
            throws DatabaseException {
        Table table = getTable(tableName);
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
                treeIndex.remove(oldRow.get(index.getValue()), key);
                treeIndex.put(newRow.get(index.getValue()), key);
            }
        }
        save(table);
        return count;
    }

    /**
     * Удаляет строки, подходящие под условие (DELETE FROM ... WHERE ...).
     * Нельзя удалить строку, на которую ссылается другая таблица.
     *
     * @param condition условие WHERE; null — все строки
     * @return сколько строк удалено
     * @throws DatabaseException если на удаляемую строку есть ссылка
     */
    public int delete(String tableName, Predicate<Row> condition) throws DatabaseException {
        Table table = getTable(tableName);
        String pk = table.getSchema().getPrimaryKeyColumnName();
        List<Row> rowsToDelete = table.select(null, condition);

        Set<Object> keys = new HashSet<>();
        for (Row row : rowsToDelete) {
            keys.add(row.get(pk));
        }
        checkNotReferenced(table, keys);

        int count = table.delete(condition);
        for (Row row : rowsToDelete) {
            for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
                indexes.get(index.getKey()).remove(row.get(index.getValue()), row.get(pk));
            }
        }
        save(table);
        return count;
    }

    /**
     * Выборка с просмотром всей таблицы (SELECT с любым условием).
     *
     * @param columns   нужные колонки; null или ["*"] — все
     * @param condition условие WHERE; null — все строки
     */
    public List<Row> select(String tableName, List<String> columns, Predicate<Row> condition)
            throws DatabaseException {
        return getTable(tableName).select(columns, condition);
    }

    /**
     * Выборка по условию {@code WHERE column = value}
     * Если по колонке есть индекс, строки берутся из индекса (быстро, O(log N)) иначе просматривается вся таблица
     *
     * @param value значение нужного типа (результат {@code DataType.parse})
     * @param columns нужные колонки, null или ["*"] — все
     * @throws DatabaseException если нет таблицы или колонки, или тип значения не подходит
     */
    public List<Row> selectEquals(String tableName, String columnName, Object value, List<String> columns)
            throws DatabaseException {
        Table table = getTable(tableName);
        Column column = table.getSchema().getColumn(columnName);
        if (column == null) {
            throw new DatabaseException("Колонка '" + columnName + "' не найдена в таблице '" + table.getName() + "'");
        }
        if (!column.getType().isCompatible(value)) {
            throw new DatabaseException("Значение '" + value + "' не подходит к типу " + column.getType());
        }

        TreeIndex index = findIndex(tableName, columnName);
        if (index == null) {
            return table.select(columns, row -> value != null && value.equals(row.get(columnName)));
        }

        table.checkColumnsExist(columns);
        List<Row> result = new ArrayList<>();
        for (Object key : index.get(value)) {
            result.add(table.project(table.getById(key), columns));
        }
        return result;
    }

    /**
     * Выборка по индексу для условий =, >, <, >=, <=
     * Строки берутся из дерева индекса за O(log N)
     *
     * @param op оператор сравнения
     * @param value значение нужного типа (не null)
     * @throws DatabaseException если по колонке нет индекса или оператор не поддерживается
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Row> selectByIndex(String tableName, String columnName, String op, Object value,
                                   List<String> columns) throws DatabaseException {
        Table table = getTable(tableName);
        TreeIndex index = findIndex(tableName, columnName);
        if (index == null) {
            throw new DatabaseException("По колонке '" + columnName + "' нет индекса");
        }
        table.checkColumnsExist(columns);

        List<Object> keys;
        switch (op) {
            case "=":  keys = index.get(value); break;
            case ">":  keys = index.greaterThan((Comparable) value); break;
            case "<":  keys = index.lessThan((Comparable) value); break;
            case ">=": keys = index.greaterOrEqual((Comparable) value); break;
            case "<=": keys = index.lessOrEqual((Comparable) value); break;
            default: throw new DatabaseException("Оператор '" + op + "' не поддерживает индекс");
        }

        List<Row> result = new ArrayList<>();
        for (Object key : keys) {
            Row row = table.getById(key);
            if (row != null) {
                result.add(table.project(row, columns));
            }
        }
        return result;
    }

    /**
     * Создаёт индекс (CREATE INDEX name ON table (column)), заполняет его данными таблицы и сохраняет в файл {@code <name>.idx}
     *
     * @throws DatabaseException если имя индекса занято, нет таблицы или колонки, или по колонке уже есть индекс
     */
    public void createIndex(String indexName, String tableName, String columnName) throws DatabaseException {
        String name = indexName.trim().toLowerCase();
        if (indexes.containsKey(name)) {
            throw new DatabaseException("Индекс '" + indexName + "' уже существует");
        }
        Table table = getTable(tableName);
        Column column = table.getSchema().getColumn(columnName);
        if (column == null) {
            throw new DatabaseException("Колонка '" + columnName + "' не найдена в таблице '" + table.getName() + "'");
        }
        if (findIndex(tableName, columnName) != null) {
            throw new DatabaseException("По колонке '" + column.getName() + "' уже есть индекс");
        }

        indexes.put(name, buildIndex(table, column.getName()));
        table.getIndexes().put(name, column.getName());
        save(table);
    }

    /**
     * Ищет индекс по таблице и колонке
     *
     * @return индекс или null, если колонка не проиндексирована
     */
    public TreeIndex findIndex(String tableName, String columnName) throws DatabaseException {
        for (Map.Entry<String, String> index : getTable(tableName).getIndexes().entrySet()) {
            if (index.getValue().equalsIgnoreCase(columnName.trim())) {
                return indexes.get(index.getKey());
            }
        }
        return null;
    }

    public boolean hasIndex(String tableName, String columnName) throws DatabaseException {
        return findIndex(tableName, columnName) != null;
    }

    /**
     * Сохраняет таблицу и все её индексы на диск
     */
    private void save(Table table) throws DatabaseException {
        repository.save(table);
        for (String indexName : table.getIndexes().keySet()) {
            try {
                indexes.get(indexName).save(getIndexFile(indexName).getPath());
            } catch (Exception e) {
                throw new DatabaseException("Не удалось сохранить индекс '" + indexName + "': " + e.getMessage());
            }
        }
    }

    private void loadTables() {
        for (String name : repository.listTableNames()) {
            try {
                tables.put(name.toLowerCase(), repository.load(name));
            } catch (DatabaseException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    /**
     * Загружает индексы из файлов .idx. Если файл повреждён или пропал (например, программа упала во время записи), индекс строится заново по данным таблицы
     *
     */
    private void loadIndexes() {
        for (Table table : tables.values()) {
            for (Map.Entry<String, String> index : table.getIndexes().entrySet()) {
                String indexName = index.getKey();
                File indexFile = getIndexFile(indexName);
                try {
                    indexes.put(indexName, TreeIndex.load(indexFile.getPath()));
                } catch (Exception e) {
                    System.out.println("Индекс '" + indexName + "' не прочитался, строим его заново");
                    indexes.put(indexName, buildIndex(table, index.getValue()));
                    indexFile.delete();
                }
            }
        }
    }

    private TreeIndex buildIndex(Table table, String columnName) {
        String pk = table.getSchema().getPrimaryKeyColumnName();
        TreeIndex index = new TreeIndex(table.getName(), columnName);
        for (Row row : table) {
            index.put(row.get(columnName), row.get(pk));
        }
        return index;
    }

    private File getIndexFile(String indexName) {
        return new File(dataDir, indexName + ".idx");
    }

    /**
     * Проверяет при CREATE TABLE, что внешний ключ ссылается на первичный ключ существующей таблицы того же типа
     */
    private void checkForeignKeyDefinition(Column col) throws DatabaseException {
        if (!hasTable(col.getForeignKeyTable())) {
            throw new DatabaseException("Внешний ключ '" + col.getName() + "' ссылается на несуществующую таблицу '"
                    + col.getForeignKeyTable() + "'");
        }
        Column parentPk = getTable(col.getForeignKeyTable()).getSchema().getPrimaryKeyColumn();
        if (!parentPk.getName().equalsIgnoreCase(col.getForeignKeyColumn()) || parentPk.getType() != col.getType()) {
            throw new DatabaseException("Внешний ключ '" + col.getName() + "' должен ссылаться на первичный ключ '"
                    + col.getForeignKeyTable() + "." + parentPk.getName() + "' типа " + parentPk.getType());
        }
    }

    /**
     * Проверяет, что в родительской таблице есть строка с таким первичным ключом
     */
    private void checkParentExists(Column fkColumn, Object value) throws DatabaseException {
        if (value != null && !getTable(fkColumn.getForeignKeyTable()).containsKey(value)) {
            throw new DatabaseException("Нарушение внешнего ключа: в таблице '" + fkColumn.getForeignKeyTable()
                    + "' нет записи с " + fkColumn.getForeignKeyColumn() + " = " + value);
        }
    }

    /**
     * Проверяет, что на удаляемые строки не ссылается ни одна другая таблица
     */
    private void checkNotReferenced(Table parent, Set<Object> keys) throws DatabaseException {
        for (Table child : tables.values()) {
            for (Column col : child.getSchema().getColumns()) {
                if (col.isForeignKey() && col.getForeignKeyTable().equalsIgnoreCase(parent.getName())) {
                    for (Row row : child) {
                        if (keys.contains(row.get(col.getName()))) {
                            throw new DatabaseException("Нельзя удалить запись из '" + parent.getName()
                                    + "': на неё ссылается таблица '" + child.getName() + "'");
                        }
                    }
                }
            }
        }
    }
}
