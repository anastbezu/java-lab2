package org.example;

import org.example.model.QueryResult;
import org.example.model.Row;

import java.util.ArrayList;
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
            } catch (Exception e) {

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

    public void printResult(QueryResult result) {
        if (!result.isSuccess()) {
            System.out.println("ОШИБКА: " + result.getMessage());
            return;
        }

        if (result.getColumns().isEmpty()) {
            System.out.println(result.getMessage() + " (" + result.getExecutionTimeMs() + " мс)");
            return;
        }

        // сюда позже добавим вывод таблицы для SELECT

        // 1. шапка
        for (String col : result.getColumns()) {
            System.out.printf("| %-15s ", col);
        }
        System.out.println("|");

        // 2. линия
        for (int i = 0; i < result.getColumns().size(); i++) {
            System.out.print("+-----------------");
        }
        System.out.println("+");

        // 3. строки данных
        for (Row row : result.getRows()) {
            for (String col : result.getColumns()) {
                Object val = row.get(col);
                System.out.printf("| %-15s ", val == null ? "NULL" : val.toString());
            }
            System.out.println("|");
        }

        // 4. итог
        System.out.println(result.getMessage() + " (" + result.getExecutionTimeMs() + " мс)");
    }
}
