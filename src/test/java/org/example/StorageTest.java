package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.storage.Database;
import org.example.storage.Table;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты слоя хранения: типы, ключи, UPDATE/DELETE, индексы и сохранение на диск.
 */
public class StorageTest {

    @TempDir
    File tempDir;

    private Database database;

    @BeforeEach
    public void setUp() throws Exception {
        database = new Database(tempDir);
        database.createTable(new TableSchema("manufacturers", Arrays.asList(
                new Column("id", DataType.INT, true),
                new Column("name", DataType.STRING),
                new Column("country", DataType.STRING),
                new Column("is_active", DataType.BOOLEAN))));
        database.createTable(new TableSchema("cars", Arrays.asList(
                new Column("id", DataType.INT, true),
                new Column("brand", DataType.STRING),
                new Column("price", DataType.FLOAT),
                new Column("release_date", DataType.DATE),
                new Column("in_stock", DataType.BOOLEAN),
                new Column("manufacturer_id", DataType.INT, "manufacturers", "id"))));
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
        assertThrows(IllegalArgumentException.class, () -> DataType.INT.parse("abc"));
    }

    @Test
    public void testWrongTypeRejected() {
        Row row = car(1, "M5", 0, 1);
        row.set("price", 90000);
        assertThrows(DatabaseException.class, () -> database.insertInto("cars", row));
    }

    @Test
    public void testCreateRowFromQueryValues() throws Exception {
        TableSchema schema = cars().getSchema();
        Row row = schema.createRowFromNamedValues(Arrays.asList("id", "brand"), Arrays.asList("2", "'BMW'"));
        database.insertInto("cars", row);
        assertEquals("BMW", cars().getById(2).get("brand"));
        assertNull(cars().getById(2).get("price"));
    }

    @Test
    public void testInsertAndSelect() throws Exception {
        database.insertInto("cars", car(101, "M5", 85000.0, 1));
        List<Row> rows = database.select("cars", Arrays.asList("brand", "price"), row -> (Double) row.get("price") > 20000);
        assertEquals(1, rows.size());
        assertEquals("M5", rows.get(0).get("brand"));
        assertNull(rows.get(0).get("id"), "В результате должны быть только brand и price");
    }

    @Test
    public void testDuplicatePrimaryKeyRejected() throws Exception {
        assertThrows(DatabaseException.class, () -> database.insertInto("manufacturers", manufacturer(1, "Honda")));
    }

    @Test
    public void testBatchInsertWithDuplicateInsertsNothing() {
        List<Row> batch = Arrays.asList(car(1, "M5", 1.0, 1), car(2, "X5", 1.0, 1), car(1, "Dup", 1.0, 1));
        assertThrows(DatabaseException.class, () -> database.insertAll("cars", batch));
        assertDoesNotThrow(() -> assertEquals(0, cars().size()));
    }

    @Test
    public void testMissingParentRejected() {
        assertThrows(DatabaseException.class, () -> database.insertInto("cars", car(1, "Ghost", 1.0, 999)));
    }

    @Test
    public void testDeleteReferencedParentRejected() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        assertThrows(DatabaseException.class,
                () -> database.delete("manufacturers", row -> Integer.valueOf(1).equals(row.get("id"))));
        assertEquals(1, database.getTable("manufacturers").size());
    }

    @Test
    public void testUpdateToMissingParentRejected() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        assertThrows(DatabaseException.class, () -> database.update("cars", null, set("manufacturer_id", 999)));
    }

    @Test
    public void testUpdatePrimaryKeyRejected() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        assertThrows(DatabaseException.class, () -> database.update("cars", null, set("id", 5)));
    }

    @Test
    public void testTableWithoutPrimaryKeyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TableSchema("logs", Arrays.asList(new Column("msg", DataType.STRING))));
    }

    @Test
    public void testForeignKeyToMissingTableRejected() {
        TableSchema pets = new TableSchema("pets", Arrays.asList(
                new Column("id", DataType.INT, true),
                new Column("owner_id", DataType.INT, "people", "id")));
        assertThrows(DatabaseException.class, () -> database.createTable(pets));
    }

    @Test
    public void testUpdateAndDeleteAreSavedToDisk() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));

        assertEquals(1, database.update("cars", row -> Integer.valueOf(1).equals(row.get("id")), set("price", 95000.0)));
        assertEquals(1, database.delete("cars", row -> "X5".equals(row.get("brand"))));

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
        assertThrows(DatabaseException.class, () -> database.dropTable("unknown"));
    }

    @Test
    public void testIndexFollowsInsertUpdateDelete() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));
        database.createIndex("idx_cars_brand", "cars", "brand");
        TreeIndex index = database.findIndex("cars", "brand");

        database.insertInto("cars", car(3, "M5", 95000.0, 1));
        assertEquals(Arrays.asList(1, 3), index.get("M5"));

        database.update("cars", row -> Integer.valueOf(2).equals(row.get("id")), set("brand", "X6"));
        assertTrue(index.get("X5").isEmpty());
        assertEquals(Arrays.asList(2), index.get("X6"));

        database.delete("cars", row -> Integer.valueOf(1).equals(row.get("id")));
        assertEquals(Arrays.asList(3), index.get("M5"));
    }

    @Test
    public void testSelectEqualsSameWithAndWithoutIndex() throws Exception {
        database.insertInto("cars", car(1, "M5", 90000.0, 1));
        database.insertInto("cars", car(2, "X5", 70000.0, 1));
        database.insertInto("cars", car(3, "M5", 95000.0, 1));

        List<Row> withoutIndex = database.selectEquals("cars", "brand", "M5", Arrays.asList("id"));
        database.createIndex("idx_cars_brand", "cars", "brand");
        List<Row> withIndex = database.selectEquals("cars", "brand", "M5", Arrays.asList("id"));

        assertEquals(2, withIndex.size());
        assertEquals(withoutIndex, withIndex);
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

        try (FileWriter writer = new FileWriter(new File(tempDir, "idx_cars_brand.idx"))) {
            writer.write("broken");
        }

        Database restarted = new Database(tempDir);
        assertEquals(Arrays.asList(1), restarted.findIndex("cars", "brand").get("M5"));
    }
}
