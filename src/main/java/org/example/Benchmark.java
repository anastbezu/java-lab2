package org.example;

import org.example.model.Column;
import org.example.model.DataType;
import org.example.model.Row;
import org.example.model.TableSchema;
import org.example.storage.Database;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Замеры производительности SELECT на 50 000 строк (без индекса и с индексом)
 */
public class Benchmark {
    /**
     * Запускает замеры и выводит результаты в консоль
     *
     * @param args аргументы командной строки не используются
     * @throws Exception если не удалось создать таблицу или выполнить запрос
     */
    public static void main(String[] args) throws Exception {
        // чистим данные прошлого запуска
        File dir = new File("bench_data");
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete(); // удаляем .db и .idx файлы
                }
            }
            dir.delete();
        }

        Database db = new Database(dir);

        db.createTable(new TableSchema("cars", Arrays.asList(
                new Column("id", DataType.INT, true),
                new Column("brand", DataType.STRING),
                new Column("price", DataType.FLOAT),
                new Column("release_date", DataType.DATE),
                new Column("in_stock", DataType.BOOLEAN))));
        String[] brands = {"BMW", "Toyota", "Audi", "Honda", "Ford"};
        Random random = new Random(42);
        List<Row> rows = new ArrayList<>();

        for (int i = 1; i <= 50000; i++) {
            Row row = new Row();
            row.set("id", i);
            row.set("brand", brands[random.nextInt(brands.length)]);
            row.set("price", 10000.0 + random.nextInt(90000));
            row.set("release_date", LocalDate.of(2000 + random.nextInt(25), 1, 1));
            row.set("in_stock", random.nextBoolean());
            rows.add(row);
        }
        db.insertAll("cars", rows);
        System.out.println("Вставлено строк: " + rows.size());
        List<String> cols = Arrays.asList("id");

        // цена, которая точно есть в таблице, и узкий диапазон
        double existingPrice = (Double) rows.get(25000).get("price");
        double rangeFrom = 99000.0;

        // прогрев, чтобы первый замер не был занижен
        for (int i = 0; i < 20; i++) {
            db.selectEquals("cars", "brand", "BMW", cols);
            db.select("cars", cols, row -> (Double) row.get("price") > rangeFrom);
        }

        int found = 0;

        // 1. brand = 'BMW' (низкая)
        int repeats = 100;
        long t1 = System.nanoTime();
        for (int i = 0; i < repeats; i++) {
            found = db.selectEquals("cars", "brand", "BMW", cols).size();
        }
        long brandNo = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("brand = 'BMW', без индекса: " + brandNo + " мс (найдено " + found + ")");

        long tb = System.nanoTime();
        db.createIndex("idx_cars_brand", "cars", "brand");
        System.out.println("Построение индекса по brand: " + (System.nanoTime() - tb) / 1_000_000 + " мс");

        long t2 = System.nanoTime();
        for (int i = 0; i < repeats; i++) {
            found = db.selectEquals("cars", "brand", "BMW", cols).size();
        }
        long brandWith = (System.nanoTime() - t2) / 1_000_000;
        System.out.println("brand = 'BMW', с индексом: " + brandWith + " мс (найдено " + found + ")");
        printSpeedup(brandNo, brandWith);

        // 2. price = существующее значение (высокая)
        int repeats2 = 1000;
        long p1 = System.nanoTime();
        for (int i = 0; i < repeats2; i++) {
            found = db.selectEquals("cars", "price", existingPrice, cols).size();
        }
        long priceNo = (System.nanoTime() - p1) / 1_000_000;
        System.out.println("price = " + existingPrice + ", без индекса: " + priceNo + " мс (найдено " + found + ")");

        // 3. Диапазон - price > 99000
        int repeats3 = 100;
        long r1 = System.nanoTime();
        for (int i = 0; i < repeats3; i++) {
            found = db.select("cars", cols, row -> (Double) row.get("price") > rangeFrom).size();
        }
        long rangeNo = (System.nanoTime() - r1) / 1_000_000;
        System.out.println("price > " + rangeFrom + ", без индекса: " + rangeNo + " мс (найдено " + found + ")");

        db.createIndex("idx_cars_price", "cars", "price");

        long p2 = System.nanoTime();
        for (int i = 0; i < repeats2; i++) {
            found = db.selectEquals("cars", "price", existingPrice, cols).size();
        }
        long priceWith = (System.nanoTime() - p2) / 1_000_000;
        System.out.println("price = " + existingPrice + ", с индексом: " + priceWith + " мс (найдено " + found + ")");
        printSpeedup(priceNo, priceWith);

        long r2 = System.nanoTime();
        for (int i = 0; i < repeats3; i++) {
            found = db.selectByIndex("cars", "price", ">", rangeFrom, cols).size();
        }
        long rangeWith = (System.nanoTime() - r2) / 1_000_000;
        System.out.println("price > " + rangeFrom + ", с индексом: " + rangeWith + " мс (найдено " + found + ")");
        printSpeedup(rangeNo, rangeWith);

        System.out.println("Повторов: brand " + repeats + ", price = " + repeats2 + ", диапазон " + repeats3);
    }

    /**
     * Печатает, во сколько раз выборка с индексом быстрее выборки без него
     *
     * @param without время без индекса, мс
     * @param with время с индексом, мс
     */
    private static void printSpeedup(long without, long with) {
        if (with > 0) {
            System.out.printf("Ускорение: в %.1f раз%n", (double) without / with);
        } else {
            System.out.println("Ускорение: с индексом < 1 мс, в разы быстрее");
        }
    }
}