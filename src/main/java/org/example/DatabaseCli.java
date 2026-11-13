package org.example;

import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.storage.Database;

import java.util.List;
import java.util.Scanner;

/**
 * Консольный интерфейс мини-СУБД.
 * Читает SQL-команды пользователя в цикле и выводит результат
 */
public class DatabaseCli {

    private final Database db;
    private final SqlParser parser = new SqlParser();

    /**
     * Создаёт консольный интерфейс для указанной базы данных
     *
     * @param db база данных, над которой выполняются запросы
     */
    public DatabaseCli(Database db) {
        this.db = db;
    }

    /**
     * Запускает цикл чтения и выполнения команд до ввода exit
     */
    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Мини-СУБД. Введите SQL-запрос или exit для выхода.");

        while (true) {
            System.out.print("db> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("exit")) {
                System.out.println("exit");
                break;
            }
            try {
                QueryResult result = parser.parse(line).execute(db);
                printResult(result);
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    /**
     * Результат запроса в виде таблицы
     *
     * @param cols названия колонок (шапка таблицы)
     * @param rows строки данных (каждая строка - список значений)
     */
    public void printTable(List<String> cols, List<List<Object>> rows) {
        for (String col : cols) {
            System.out.printf("| %-15s ", col);
        }
        System.out.println("|");

        for (int ii = 0; ii < cols.size(); ii++) {
            System.out.print("+-----------------");
        }
        System.out.println("+");

        for (List<Object> row : rows) {
            for (Object val : row) {
                System.out.printf("| %-15s ", val == null ? "NULL" : val.toString());
            }
            System.out.println("|");
        }

        System.out.println("Rows count: " + rows.size());
    }

    /**
     * Выводит результат запроса: таблицу для SELECT или сообщение для остальных команд
     *
     * @param result результат выполнения команды
     */
    public void printResult(QueryResult result) {
        if (!result.isSuccess()) {
            System.out.println("ОШИБКА: " + result.getMessage());
            return;
        }

        if (result.getColumns().isEmpty()) {
            System.out.println(result.getMessage() + " (" + result.getExecutionTimeMs() + " мс)");
            return;
        }

        List<String> cols = result.getColumns();
        int[] w = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) w[i] = cols.get(i).length();
        for (Row row : result.getRows()) {
            for (int i = 0; i < cols.size(); i++) {
                Object v = row.get(cols.get(i));
                w[i] = Math.max(w[i], (v == null ? "NULL" : v.toString()).length());
            }
        }
        StringBuilder sep = new StringBuilder("+");
        for (int x : w) {
            for (int k = 0; k < x + 2; k++) sep.append('-');
            sep.append('+');
        }
        System.out.println(sep);
        for (int i = 0; i < cols.size(); i++) System.out.printf("| %-" + w[i] + "s ", cols.get(i));
        System.out.println("|");
        System.out.println(sep);
        for (Row row : result.getRows()) {
            for (int i = 0; i < cols.size(); i++) {
                Object v = row.get(cols.get(i));
                System.out.printf("| %-" + w[i] + "s ", v == null ? "NULL" : v.toString());
            }
            System.out.println("|");
        }
        System.out.println(sep);

        System.out.println(result.getMessage() + " (" + result.getExecutionTimeMs() + " мс)");
    }
}