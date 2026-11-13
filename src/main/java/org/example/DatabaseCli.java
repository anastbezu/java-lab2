package org.example;

import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.storage.Database;

import java.util.List;
import java.util.Scanner;

/**
 * Консольный интерфейс мини-СУБД читает SQL-команды пользователя в цикле и выводит результат
 *
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
     * Запускает цикл чтения и выполнения команд до ввода {@code exit}
     */
    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Мини-СУБД. Введите SQL-запрос или exit для выхода.");

        while (true) {
            System.out.print("db> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
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
     * Выводит результат запроса - таблицу для SELECT или сообщение для остальных команд
     * Ширина столбцов подбирается по самому длинному значению
     *
     * @param result результат выполнения команды
     */
    public void printResult(QueryResult result) {
        if (!result.isSuccess()) {
            System.out.println("ОШИБКА: " + result.getMessage());
            return;
        }

        List<String> columns = result.getColumns();
        if (columns.isEmpty()) {
            System.out.println(result.getMessage() + " (" + result.getExecutionTimeMs() + " мс)");
            return;
        }

        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).length();
        }
        for (Row row : result.getRows()) {
            for (int i = 0; i < columns.size(); i++) {
                widths[i] = Math.max(widths[i], cellText(row.get(columns.get(i))).length());
            }
        }

        StringBuilder separator = new StringBuilder("+");
        for (int width : widths) {
            for (int i = 0; i < width + 2; i++) {
                separator.append('-');
            }
            separator.append('+');
        }

        System.out.println(separator);
        for (int i = 0; i < columns.size(); i++) {
            System.out.printf("| %-" + widths[i] + "s ", columns.get(i));
        }
        System.out.println("|");
        System.out.println(separator);
        for (Row row : result.getRows()) {
            for (int i = 0; i < columns.size(); i++) {
                System.out.printf("| %-" + widths[i] + "s ", cellText(row.get(columns.get(i))));
            }
            System.out.println("|");
        }
        System.out.println(separator);

        System.out.println(result.getMessage() + " (" + result.getExecutionTimeMs() + " мс)");
    }

    private String cellText(Object value) {
        return value == null ? "NULL" : value.toString();
    }
}