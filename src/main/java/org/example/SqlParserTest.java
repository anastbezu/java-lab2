package org.example;

import java.util.Arrays;

public class SqlParserTest {

    private static final SqlParser parser = new SqlParser();
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        check("SELECT", () ->
                assertType("SELECT * FROM cars", SelectCommand.class));

        check("SELECT WHERE", () ->
                assertType("SELECT * FROM cars WHERE price > 10000",
                        SelectCommand.class));

        check("INSERT", () ->
                assertType("INSERT INTO cars VALUES (1, 'Toyota')",
                        InsertCommand.class));

        check("INSERT со строкой и запятой", () ->
                assertType("INSERT INTO cars (id, brand) VALUES (1, 'Toyota, Camry')",
                        InsertCommand.class));

        check("INSERT нескольких записей", () ->
                assertType("INSERT INTO cars VALUES (1, 'Toyota'), (2, 'BMW')",
                        InsertCommand.class));

        check("UPDATE", () ->
                assertType("UPDATE cars SET price = 30000 WHERE id = 1",
                        UpdateCommand.class));

        check("DELETE", () ->
                assertType("DELETE FROM cars WHERE id = 1",
                        DeleteCommand.class));

        check("CREATE INDEX", () ->
                assertType("CREATE INDEX idx_brand ON cars (brand)",
                        CreateIndexCommand.class));

        check("CREATE TABLE", () ->
                assertType("CREATE TABLE cars (id INT PRIMARY KEY, brand STRING)",
                        CreateTableCommand.class));

        check("FOREIGN KEY", () ->
                assertType("CREATE TABLE cars (id INT PRIMARY KEY, manufacturer_id INT REFERENCES manufacturers(id))",
                        CreateTableCommand.class));

        check("DROP TABLE", () ->
                assertType("DROP TABLE cars", DropTableCommand.class));

        check("WHERE со строкой и пробелом", () ->
                assertArray("brand = 'Toyota Camry'",
                        "brand", "=", "Toyota Camry"));

        check("WHERE с числом", () ->
                assertArray("price = 30000.0",
                        "price", "=", "30000.0"));

        check("Неизвестная команда", () ->
                assertError("UNKNOWN COMMAND"));

        check("SELECT без FROM", () ->
                assertError("SELECT * cars"));

        check("INSERT с незакрытыми кавычками", () ->
                assertError("INSERT INTO cars VALUES (1, 'Toyota)"));

        System.out.println("\n========== РЕЗУЛЬТАТ ==========");
        System.out.println("Пройдено: " + passed);
        System.out.println("Провалено: " + failed);
    }

    private static void check(String name, Test test) {
        try {
            test.run();
            passed++;
            System.out.println("PASS: " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL: " + name + " — " + e.getMessage());
        }
    }

    private static void assertType(String sql, Class<?> expected) throws Exception {
        Object result = parser.parse(sql);

        if (!expected.isInstance(result)) {
            throw new AssertionError("Получен неправильный тип команды");
        }
    }

    private static void assertArray(String sql, String... expected) {
        String[] actual = SqlParser.parseConditionParts(sql);

        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("Неверный разбор WHERE");
        }
    }

    private static void assertError(String sql) throws Exception {
        try {
            parser.parse(sql);
            throw new AssertionError("Ожидалась ошибка SQL");
        } catch (SqlException e) {
            // Ожидаемая ошибка
        }
    }

    @FunctionalInterface
    interface Test {
        void run() throws Exception;
    }
}