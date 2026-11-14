package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.model.WhereCondition;
import org.example.storage.Database;
import org.example.storage.Table;
import org.example.storage.TreeIndex;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты слоя хранения: типы, ключи, UPDATE и DELETE, индексы и сохранение на диск.
 */
public class StorageTest {

    @TempDir
    File tempDir;

    private Database database;

    @BeforeEach
    public void setUp() throws Exception {
        database = new Database(tempDir);

        List<Column> manufacturerColumns = new ArrayList<>();
        manufacturerColumns.add(new Column("id", DataType.INT, true));
        manufacturerColumns.add(new Column("name", DataType.STRING));
        manufacturerColumns.add(new Column("country", DataType.STRING));
        manufacturerColumns.add(new Column("is_active", DataType.BOOLEAN));
        database.createTable(new TableSchema("manufacturers", manufacturerColumns));

        List<Column> carColumns = new ArrayList<>();
        carColumns.add(new Column("id", DataType.INT, true));
        carColumns.add(new Column("brand", DataType.STRING));
        carColumns.add(new Column("price", DataType.FLOAT));
        carColumns.add(new Column("release_date", DataType.DATE));
        carColumns.add(new Column("in_stock", DataType.BOOLEAN));
        carColumns.add(new Column("manufacturer_id", DataType.INT, "manufacturers", "id"));
        database.createTable(new TableSchema("cars", carColumns));

        database.insertInto("manufacturers", manufacturer(1, "BMW"));
    }

    private Row manufacturer(int id, String name) {
        Row row = new Row();
        row.set("id", id);
        row.set("name", name);
        row.set("country", "Germany");
        row.set("is_active", true);
        return row;
    }

    private Row car(int id, String brand, double price, int manufacturerId) {
        Row row = new Row();
        row.set("id", id);
        row.set("brand", brand);
        row.set("price", price);
        row.set("release_date", LocalDate.of(2022, 1, 1));
        row.set("in_stock", true);
        row.set("manufacturer_id", manufacturerId);
        return row;
    }

    private Map<String, Object> set(String column, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(column, value);
        return values;
    }

    private Table cars() throws DatabaseException {
        return database.getTable("cars");
    }

    @Test
    public void testDataTypeParsing() {
        assertEquals(10, DataType.INT.parse("10"));
        assertEquals(25000.5, DataType.FLOAT.parse("25000.5"));
        assertEquals("Toyota", DataType.STRING.parse("'Toyota'"));
        assertEquals(LocalDate.of(2023, 5, 10), DataType.DATE.parse("'2023-05-10'"));
        assertEquals(true, DataType.BOOLEAN.parse("TRUE"));
        assertNull(DataType.INT.parse("NULL"));

        boolean failed = false;
        try {
            DataType.INT.parse("abc");
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed, "Строка abc не должна превращаться в INT");
    }

    @Test
    public void testWrongTypeRejected() {
        Row row = car(1, "M5", 0, 1);
        row.set("price", 90000);

        boolean failed = false;
        try {
            database.insertInto("cars", row);
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "INT в колонку FLOAT записываться не должен");
    }

    @Test
    public void testCreateRowFromQueryValues() throws Exception {
        TableSchema schema = cars().getSchema();
        List<String> names = Arrays.asList("id", "brand");
        List<String> values = Arrays.asList("2", "'BMW'");
        Row row = schema.createRowFromNamedValues(names, values);
        database.insertInto("cars", row);

        assertEquals("BMW", cars().getById(2).get("brand"));
        assertNull(cars().getById(2).get("price"));
    }

    @Test
    public void testInsertAndSelect() throws Exception {
        database.insertInto("cars", car(101, "M5", 85000.0, 1));
        List<String> columns = Arrays.asList("brand", "price");
        WhereCondition expensive = new WhereCondition("price", ">", "20000");
        List<Row> rows = database.select("cars", columns, expensive);

        assertEquals(1, rows.size());
        assertEquals("M5", rows.get(0).get("brand"));
        assertNull(rows.get(0).get("id"), "В результате должны быть только brand и price");
    }

    @Test
    public void testDuplicatePrimaryKeyRejected() {
        boolean failed = false;
        try {
            database.insertInto("manufacturers", manufacturer(1, "Honda"));
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Второй производитель с id = 1 вставляться не должен");
    }

    @Test
    public void testBatchInsertWithDuplicateInsertsNothing() throws Exception {
        List<Row> batch = new ArrayList<>();
        batch.add(car(1, "M5", 1.0, 1));
        batch.add(car(2, "X5", 1.0, 1));
        batch.add(car(1, "Dup", 1.0, 1));

        boolean failed = false;
        try {
            database.insertAll("cars", batch);
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Пачка с повтором id должна отклоняться");
        assertEquals(0, cars().size());
    }

    @Test
    public void testMissingParentRejected() {
        boolean failed = false;
        try {
            database.insertInto("cars", car(1, "Ghost", 1.0, 999));
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Машина с несуществующим производителем вставляться не должна");
    }

    @Test
    public void testDeleteReferencedParentRejected() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));

        boolean failed = false;
        try {
            database.delete("manufacturers", new WhereCondition("id", "=", "1"));
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Производителя, на которого ссылается машина, удалять нельзя");
        assertEquals(1, database.getTable("manufacturers").size());
    }

    @Test
    public void testUpdateToMissingParentRejected() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));

        boolean failed = false;
        try {
            database.update("cars", null, set("manufacturer_id", 999));
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Ссылку на несуществующего производителя ставить нельзя");
    }

    @Test
    public void testUpdatePrimaryKeyRejected() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));

        boolean failed = false;
        try {
            database.update("cars", null, set("id", 5));
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Первичный ключ через UPDATE менять нельзя");
    }

    @Test
    public void testTableWithoutPrimaryKeyRejected() {
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("msg", DataType.STRING));

        boolean failed = false;
        try {
            new TableSchema("logs", columns);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        assertTrue(failed, "Таблица без первичного ключа создаваться не должна");
    }

    @Test
    public void testForeignKeyToMissingTableRejected() {
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("id", DataType.INT, true));
        columns.add(new Column("owner_id", DataType.INT, "people", "id"));
        TableSchema pets = new TableSchema("pets", columns);

        boolean failed = false;
        try {
            database.createTable(pets);
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Внешний ключ на несуществующую таблицу должен отклоняться");
    }

    @Test
    public void testUpdateAndDeleteAreSavedToDisk() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));

        int updated = database.update("cars", new WhereCondition("id", "=", "1"), set("price", 95000.0));
        int deleted = database.delete("cars", new WhereCondition("brand", "=", "X5"));
        assertEquals(1, updated);
        assertEquals(1, deleted);

        Database restarted = new Database(tempDir);
        Table cars = restarted.getTable("cars");
        assertEquals(1, cars.size());
        assertEquals(95000.0, cars.getById(1).get("price"));
    }

    @Test
    public void testIterator() throws Exception {
        database.insertInto("cars", car(1, "M5", 1.0, 1));
        database.insertInto("cars", car(2, "X5", 1.0, 1));
        int count = 0;
        for (Row row : cars()) {
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testDropTable() throws Exception {
        database.createIndex("idx_cars_brand", "cars", "brand");
        database.dropTable("cars");
        assertFalse(database.hasTable("cars"));
        assertFalse(new File(tempDir, "cars.db").exists());
        assertFalse(new File(tempDir, "idx_cars_brand.idx").exists());

        boolean failed = false;
        try {
            database.dropTable("unknown");
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, "Удаление несуществующей таблицы должно давать ошибку");
    }

    @Test
    public void testIndexFollowsInsertUpdateDelete() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));
        database.createIndex("idx_cars_brand", "cars", "brand");
        TreeIndex index = database.findIndex("cars", "brand");

        database.insertInto("cars", car(3, "M5", 95000.0, 1));
        assertEquals(Arrays.asList(1, 3), index.get("M5"));

        database.update("cars", new WhereCondition("id", "=", "2"), set("brand", "X6"));
        assertTrue(index.get("X5").isEmpty());
        assertEquals(Arrays.asList(2), index.get("X6"));

        database.delete("cars", new WhereCondition("id", "=", "1"));
        assertEquals(Arrays.asList(3), index.get("M5"));
    }

    @Test
    public void testSelectEqualsSameWithAndWithoutIndex() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));
        database.insertInto("cars", car(3, "M5", 95000.0, 1));
        List<String> columns = Arrays.asList("id");

        List<Row> withoutIndex = database.selectEquals("cars", "brand", "M5", columns);
        database.createIndex("idx_cars_brand", "cars", "brand");
        List<Row> withIndex = database.selectEquals("cars", "brand", "M5", columns);

        assertEquals(2, withIndex.size());
        assertEquals(withoutIndex, withIndex);
    }

    @Test
    public void testRangeSelectSameWithAndWithoutIndex() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));
        database.insertInto("cars", car(3, "X6", 95000.0, 1));
        List<String> columns = Arrays.asList("id");
        WhereCondition expensive = new WhereCondition("price", ">", "80000");
        WhereCondition cheap = new WhereCondition("price", "<", "80000");

        List<Row> expensiveWithout = database.select("cars", columns, expensive);
        List<Row> cheapWithout = database.select("cars", columns, cheap);
        database.createIndex("idx_cars_price", "cars", "price");
        List<Row> expensiveWith = database.select("cars", columns, expensive);
        List<Row> cheapWith = database.select("cars", columns, cheap);

        assertEquals(2, expensiveWith.size());
        assertEquals(expensiveWithout, expensiveWith);
        assertEquals(1, cheapWith.size());
        assertEquals(cheapWithout, cheapWith);
    }

    @Test
    public void testGreaterOrEqualAndLessOrEqual() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));
        database.insertInto("cars", car(3, "X6", 95000.0, 1));
        List<String> columns = Arrays.asList("id");
        WhereCondition atLeast = new WhereCondition("price", ">=", "90000");
        WhereCondition atMost = new WhereCondition("price", "<=", "90000");

        List<Row> atLeastWithout = database.select("cars", columns, atLeast);
        List<Row> atMostWithout = database.select("cars", columns, atMost);
        database.createIndex("idx_cars_price", "cars", "price");
        List<Row> atLeastWith = database.select("cars", columns, atLeast);
        List<Row> atMostWith = database.select("cars", columns, atMost);

        // через индекс строки идут по возрастанию цены, поэтому сравниваем состав, а не порядок
        assertEquals(2, atLeastWithout.size());
        assertEquals(2, atMostWithout.size());
        assertEquals(atLeastWithout.size(), atLeastWith.size());
        assertTrue(atLeastWith.containsAll(atLeastWithout));
        assertEquals(atMostWithout.size(), atMostWith.size());
        assertTrue(atMostWith.containsAll(atMostWithout));
    }

    @Test
    public void testGreaterOrEqualOnStringRejected() {
        boolean failed = false;
        try {
            database.select("cars", null, new WhereCondition("brand", ">=", "A"));
        } catch (DatabaseException e) {
            failed = true;
        }
        assertTrue(failed, ">= для строковой колонки должен отклоняться");
    }

    @Test
    public void testIndexRestoredAfterRestart() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.createIndex("idx_cars_brand", "cars", "brand");
        assertTrue(new File(tempDir, "idx_cars_brand.idx").exists());

        Database restarted = new Database(tempDir);
        assertTrue(restarted.hasIndex("cars", "brand"));
        restarted.insertInto("cars", car(2, "M5", 91000.0, 1));
        assertEquals(Arrays.asList(1, 2), restarted.findIndex("cars", "brand").get("M5"));
    }

    @Test
    public void testBrokenIndexFileIsRebuilt() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.createIndex("idx_cars_brand", "cars", "brand");

        File indexFile = new File(tempDir, "idx_cars_brand.idx");
        try (FileWriter writer = new FileWriter(indexFile)) {
            writer.write("broken");
        }

        Database restarted = new Database(tempDir);
        assertEquals(Arrays.asList(1), restarted.findIndex("cars", "brand").get("M5"));
    }
}
