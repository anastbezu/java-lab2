package org.example;

import org.example.exception.DatabaseException;
import org.example.exception.SqlException;
import org.example.model.QueryResult;
import org.example.model.Row;
import org.example.sql.QueryCommand;
import org.example.sql.SqlParser;
import org.example.storage.Database;

import java.util.List;
import java.util.Scanner;

/**
 * Консоль мини-СУБД: в цикле читает SQL-запросы и печатает результат.
 */
public class DatabaseCli {
    private final Database db;
    private final SqlParser parser = new SqlParser();

    /**
     * @param db база, над которой выполняются запросы
     */
    public DatabaseCli(Database db) {
        this.db = db;
    }

    /**
     * Читает и выполняет команды, пока пользователь не введёт exit.
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
                System.out.println("До встречи!");
                break;
            }
            try {
                QueryCommand command = parser.parse(line);
                QueryResult result = command.execute(db);
                printResult(result);
            } catch (SqlException e) {
                System.out.println("Ошибка в запросе: " + e.getMessage());
            } catch (DatabaseException e) {
                System.out.println("Не получилось: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("Что-то пошло не так: " + e);
            }
        }
    }

    /**
     * Печатает результат: для SELECT строки через " | ", для остальных команд только сообщение.
     *
     * @param result результат команды
     */
    public void printResult(QueryResult result) {
        List<String> columns = result.getColumns();
        if (!columns.isEmpty()) {
            String header = "";
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    header += " | ";
                }
                header += columns.get(i);
            }
            System.out.println(header);

            String separator = "";
            for (int i = 0; i < header.length(); i++) {
                separator += "-";
            }
            System.out.println(separator);

            for (Row row : result.getRows()) {
                String line = "";
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        line += " | ";
                    }
                    Object value = row.get(columns.get(i));
                    line += cellText(value);
                }
                System.out.println(line);
            }
        }
        System.out.println(result.getMessage() + " (" + result.getExecutionTimeMs() + " мс)");
    }

    private String cellText(Object value) {
        if (value == null) {
            return "NULL";
        }
        return value.toString();
    }
}
