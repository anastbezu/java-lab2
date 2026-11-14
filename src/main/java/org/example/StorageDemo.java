package org.example;

import org.example.exception.DatabaseException;
import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.model.WhereCondition;
import org.example.storage.Database;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Показывает работу слоя хранения без парсера: таблицы, ключи, индекс и сохранение на диск.
 * Данные пишутся во временную папку data/demo, в конце она удаляется.
 */
public class StorageDemo {

    /**
     * Запускает демонстрацию.
     *
     * @param args аргументы не используются
     * @throws Exception если что-то пошло не так с базой
     */
    public static void main(String[] args) throws Exception {
        File dataDir = new File("data/demo");
        Database db = new Database(dataDir);

        try {
            System.out.println("1. Создаём таблицы manufacturers и cars (cars.manufacturer_id -> manufacturers.id)");
            List<Column> manufacturerColumns = new ArrayList<>();
            manufacturerColumns.add(new Column("id", DataType.INT, true));
            manufacturerColumns.add(new Column("name", DataType.STRING));
            manufacturerColumns.add(new Column("country", DataType.STRING));
            manufacturerColumns.add(new Column("is_active", DataType.BOOLEAN));
            db.createTable(new TableSchema("manufacturers", manufacturerColumns));

            List<Column> carColumns = new ArrayList<>();
            carColumns.add(new Column("id", DataType.INT, true));
            carColumns.add(new Column("brand", DataType.STRING));
            carColumns.add(new Column("price", DataType.FLOAT));
            carColumns.add(new Column("release_date", DataType.DATE));
            carColumns.add(new Column("in_stock", DataType.BOOLEAN));
            carColumns.add(new Column("manufacturer_id", DataType.INT, "manufacturers", "id"));
            db.createTable(new TableSchema("cars", carColumns));

            System.out.println("\n2. Вставляем данные");
            db.insertInto("manufacturers", manufacturer(1, "Toyota", "Japan"));
            db.insertInto("manufacturers", manufacturer(2, "BMW", "Germany"));
            List<Row> cars = new ArrayList<>();
            cars.add(car(101, "Camry", 28000.0, 1));
            cars.add(car(102, "M5", 95000.0, 2));
            cars.add(car(103, "X5", 70000.0, 2));
            db.insertAll("cars", cars);
            printTable(db, "cars");

            System.out.println("\n3. Проверяем ключи");
            try {
                db.insertInto("cars", car(101, "Fake", 1.0, 1));
                System.out.println("   Дубликат id = 101 почему-то вставился!");
            } catch (DatabaseException e) {
                System.out.println("   Дубликат id = 101 не прошёл: " + e.getMessage());
            }
            try {
                db.insertInto("cars", car(104, "Ghost", 1.0, 999));
                System.out.println("   Машина с manufacturer_id = 999 почему-то вставилась!");
            } catch (DatabaseException e) {
                System.out.println("   Машина с manufacturer_id = 999 не прошла: " + e.getMessage());
            }
            try {
                db.delete("manufacturers", new WhereCondition("id", "=", "2"));
                System.out.println("   BMW почему-то удалилась, хотя на неё ссылаются машины!");
            } catch (DatabaseException e) {
                System.out.println("   Удалить BMW не дали: " + e.getMessage());
            }

            System.out.println("\n4. CREATE INDEX idx_cars_brand ON cars (brand) и поиск по нему");
            db.createIndex("idx_cars_brand", "cars", "brand");
            List<Row> found = db.selectEquals("cars", "brand", "M5", Arrays.asList("id", "price"));
            System.out.println("   SELECT id, price FROM cars WHERE brand = 'M5' -> " + found);

            System.out.println("\n5. UPDATE cars SET price = 99000.0 WHERE id = 102");
            Map<String, Object> newPrice = new HashMap<>();
            newPrice.put("price", 99000.0);
            db.update("cars", new WhereCondition("id", "=", "102"), newPrice);

            System.out.println("\n6. DELETE FROM cars WHERE brand = 'X5'");
            db.delete("cars", new WhereCondition("brand", "=", "X5"));

            System.out.println("\n7. Перезапуск: открываем базу заново из той же папки");
            Database restarted = new Database(dataDir);
            printTable(restarted, "cars");
            boolean indexBack = restarted.hasIndex("cars", "brand");
            System.out.println("   Индекс по brand на месте: " + indexBack);
        } finally {
            File[] files = dataDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            dataDir.delete();
        }
    }

    private static void printTable(Database db, String tableName) throws DatabaseException {
        for (Row row : db.getTable(tableName)) {
            System.out.println("   " + row);
        }
    }

    private static Row manufacturer(int id, String name, String country) {
        Row row = new Row();
        row.set("id", id);
        row.set("name", name);
        row.set("country", country);
        row.set("is_active", true);
        return row;
    }

    private static Row car(int id, String brand, double price, int manufacturerId) {
        Row row = new Row();
        row.set("id", id);
        row.set("brand", brand);
        row.set("price", price);
        row.set("release_date", LocalDate.of(2023, 1, 15));
        row.set("in_stock", true);
        row.set("manufacturer_id", manufacturerId);
        return row;
    }
}
