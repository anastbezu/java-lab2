package org.example;

import org.example.storage.Database;

import java.io.File;

/**
 * Точка входа: открывает базу в папке data и запускает консоль.
 */
public class Main {

    /**
     * Запускает мини-СУБД.
     *
     * @param args аргументы не используются
     */
    public static void main(String[] args) {
        Database db = new Database(new File("data"));

        for (String error : db.getLoadErrors()) {
            System.out.println("Не удалось загрузить: " + error);
        }

        DatabaseCli cli = new DatabaseCli(db);
        cli.start();
    }
}
