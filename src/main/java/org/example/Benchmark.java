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

public class Benchmark {
    public static void main(String[] args) throws Exception {
        //
        File dir = new File("bench_data");
        Database db = new Database(dir);

        db.createTable(new TableSchema("cars", Arrays.asList(
                new Column("id", DataType.INT, true),
                new Column("brand", DataType.STRING),
                new Column("price", DataType.FLOAT),
                new Column("release_date", DataType.DATE),
                new Column("in_stock", DataType.BOOLEAN))));
        String[] brands = {"BMW", "Toyota", "Audi", "Honda", "Ford"};
        Random random = new Random();
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

        int repeats = 100;
        long t1 = System.currentTimeMillis();
        int found = 0;
        for (int i = 0; i < repeats; i++) {
            found = db.selectEquals("cars", "brand", "BMW", Arrays.asList("id")).size();
        }
        long timeNoIndex = System.currentTimeMillis() - t1;
        System.out.println("Без индекса: " + timeNoIndex + " мс (найдено " + found + ")");

        long tb = System.currentTimeMillis();
        db.createIndex("idx_cars_brand", "cars", "brand");
        System.out.println("Построение индекса: " + (System.currentTimeMillis() - tb) + " мс");

        long t2 = System.currentTimeMillis();
        for (int i = 0; i < repeats; i++) {
            found = db.selectEquals("cars", "brand", "BMW", Arrays.asList("id")).size();
        }
        long timeWithIndex = System.currentTimeMillis() - t2;
        System.out.println("С индексом: " + timeWithIndex + " мс (найдено " + found + ")");

        if (timeWithIndex > 0) {
            System.out.println("Ускорение: в " + (double) timeNoIndex / timeWithIndex + " раз");
        }

        int repeats2 = 1000;

        long p1 = System.nanoTime();
        for (int i = 0; i < repeats2; i++) {
            db.selectEquals("cars", "price", 55000.0, Arrays.asList("id"));
        }
        long priceNoIndex = System.nanoTime() - p1;
        System.out.println("Цена, без индекса: " + priceNoIndex / 1_000_000 + " мс");

        db.createIndex("idx_cars_price", "cars", "price");

        long p2 = System.nanoTime();
        for (int i = 0; i < repeats2; i++) {
            db.selectEquals("cars", "price", 55000.0, Arrays.asList("id"));
        }
        long priceWithIndex = System.nanoTime() - p2;
        System.out.println("Цена, с индексом: " + priceWithIndex / 1_000_000.0 + " мс");

        System.out.println("Ускорение: в " + (double) priceNoIndex / priceWithIndex + " раз");
    }
}