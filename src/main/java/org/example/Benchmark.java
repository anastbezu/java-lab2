package org.example;

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
import java.util.List;
import java.util.Random;

/**
 * Замеры скорости SELECT на 50 000 строк без индекса и с индексом.
 */
public class Benchmark {

    /**
     * Запускает замеры и печатает результаты.
     *
     * @param args аргументы не используются
     * @throws Exception если не удалось создать таблицу или выполнить запрос
     */
    public static void main(String[] args) throws Exception {
        // удаляем данные прошлого запуска
        File dir = new File("bench_data");
        File[] oldFiles = dir.listFiles();
        if (oldFiles != null) {
            for (File f : oldFiles) {
                f.delete();
            }
        }
        dir.delete();

        Database db = new Database(dir);
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("id", DataType.INT, true));
        columns.add(new Column("brand", DataType.STRING));
        columns.add(new Column("price", DataType.FLOAT));
        columns.add(new Column("release_date", DataType.DATE));
        columns.add(new Column("in_stock", DataType.BOOLEAN));
        db.createTable(new TableSchema("cars", columns));

        String[] brands = {"BMW", "Toyota", "Audi", "Honda", "Ford"};
        Random random = new Random(42);
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i <= 50000; i++) {
            String brand = brands[random.nextInt(brands.length)];
            double price = 10000.0 + random.nextInt(90000);
            LocalDate releaseDate = LocalDate.of(2000 + random.nextInt(25), 1, 1);

            Row row = new Row();
            row.set("id", i);
            row.set("brand", brand);
            row.set("price", price);
            row.set("release_date", releaseDate);
            row.set("in_stock", random.nextBoolean());
            rows.add(row);
        }
        db.insertAll("cars", rows);
        System.out.println("Вставлено строк: " + rows.size());

        List<String> resultColumns = Arrays.asList("id");
        // цена, которая точно есть в таблице, и узкий диапазон дорогих машин
        double existingPrice = (Double) rows.get(25000).get("price");
        double rangeFrom = 99000.0;
        WhereCondition priceAbove = new WhereCondition("price", ">", String.valueOf(rangeFrom));

        // прогрев, чтобы первый замер не получился медленнее остальных
        for (int i = 0; i < 20; i++) {
            db.selectEquals("cars", "brand", "BMW", resultColumns);
            db.select("cars", resultColumns, priceAbove);
        }

        int found = 0;

        // 1. brand = 'BMW': каждая пятая строка подходит
        int brandRepeats = 100;
        long startBrand = System.nanoTime();
        for (int i = 0; i < brandRepeats; i++) {
            found = db.selectEquals("cars", "brand", "BMW", resultColumns).size();
        }
        long brandWithoutIndex = (System.nanoTime() - startBrand) / 1000000;
        System.out.println("brand = 'BMW', без индекса: " + brandWithoutIndex + " мс (найдено " + found + ")");

        long startBuild = System.nanoTime();
        db.createIndex("idx_cars_brand", "cars", "brand");
        long buildTime = (System.nanoTime() - startBuild) / 1000000;
        System.out.println("Построение индекса по brand: " + buildTime + " мс");

        startBrand = System.nanoTime();
        for (int i = 0; i < brandRepeats; i++) {
            found = db.selectEquals("cars", "brand", "BMW", resultColumns).size();
        }
        long brandWithIndex = (System.nanoTime() - startBrand) / 1000000;
        System.out.println("brand = 'BMW', с индексом: " + brandWithIndex + " мс (найдено " + found + ")");
        printSpeedup(brandWithoutIndex, brandWithIndex);

        // 2. price = конкретное значение: подходит всего несколько строк
        int priceRepeats = 1000;
        long startPrice = System.nanoTime();
        for (int i = 0; i < priceRepeats; i++) {
            found = db.selectEquals("cars", "price", existingPrice, resultColumns).size();
        }
        long priceWithoutIndex = (System.nanoTime() - startPrice) / 1000000;
        System.out.println("price = " + existingPrice + ", без индекса: " + priceWithoutIndex
                + " мс (найдено " + found + ")");

        // 3. диапазон price > 99000
        int rangeRepeats = 100;
        long startRange = System.nanoTime();
        for (int i = 0; i < rangeRepeats; i++) {
            found = db.select("cars", resultColumns, priceAbove).size();
        }
        long rangeWithoutIndex = (System.nanoTime() - startRange) / 1000000;
        System.out.println("price > " + rangeFrom + ", без индекса: " + rangeWithoutIndex
                + " мс (найдено " + found + ")");

        db.createIndex("idx_cars_price", "cars", "price");

        startPrice = System.nanoTime();
        for (int i = 0; i < priceRepeats; i++) {
            found = db.selectEquals("cars", "price", existingPrice, resultColumns).size();
        }
        long priceWithIndex = (System.nanoTime() - startPrice) / 1000000;
        System.out.println("price = " + existingPrice + ", с индексом: " + priceWithIndex
                + " мс (найдено " + found + ")");
        printSpeedup(priceWithoutIndex, priceWithIndex);

        startRange = System.nanoTime();
        for (int i = 0; i < rangeRepeats; i++) {
            found = db.selectByIndex("cars", "price", ">", rangeFrom, resultColumns).size();
        }
        long rangeWithIndex = (System.nanoTime() - startRange) / 1000000;
        System.out.println("price > " + rangeFrom + ", с индексом: " + rangeWithIndex
                + " мс (найдено " + found + ")");
        printSpeedup(rangeWithoutIndex, rangeWithIndex);

        System.out.println("Повторов: brand " + brandRepeats + ", price " + priceRepeats
                + ", диапазон " + rangeRepeats);
    }

    /**
     * Печатает, во сколько раз выборка с индексом быстрее.
     *
     * @param without время без индекса, мс
     * @param with    время с индексом, мс
     */
    private static void printSpeedup(long without, long with) {
        if (with > 0) {
            double speedup = (double) without / with;
            System.out.printf("Ускорение: в %.1f раз%n", speedup);
        } else {
            System.out.println("Ускорение: с индексом меньше 1 мс, то есть в разы быстрее");
        }
    }
}
