package org.example;

import java.util.List;
import java.util.Scanner;

/**
 *
 *
 * Консольный интерфейс мини-СУБД
 * Читает команды пользователя в цикле и выводит результат
 */

public class DatabaseCli {
    public void start() {
        Scanner scanner = new Scanner(System.in);

        //printTable(List.of("id", "brand"), List.of());
        //тестовый вывод таблицы
        //printTable(List.of("id", "brand"), List.of(List.of(1, "Toyota"), List.of(2, "BMW")));

        while (true) {
            System.out.print("db> ");
            String line = scanner.nextLine().trim(); //игнор пустых строк + пробелы
            if (line.isEmpty()) continue;


            if (line.equalsIgnoreCase("exit")) {
                System.out.println("exit");
                break;
            }

            try {
                System.out.println("you enter: " + line);
                //hrow new RuntimeException("тест");
            } catch (Exception e) {
                //throw new RuntimeException(e);
                System.out.println("error: " + e.getMessage());
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
        for (String col: cols){
            System.out.printf("| %-15s ", col); //лев край
        }
        System.out.println("|");

        for (int ii = 0; ii<cols.size(); ii++){
            System.out.print("+-----------------");
        }
        System.out.println("+");


        for (List<Object> row : rows) {
            for (Object val : row) {
                //null, NULL, иначе само значение
                System.out.printf("| %-15s ", val == null ? "NULL" : val.toString());
            }
            System.out.println("|");
        }


        System.out.println("Rows count: " + rows.size() );


    }
}
