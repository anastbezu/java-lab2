package org.example;
import org.example.storage.Database;
import java.io.File;

/**
 * Точка входа, открывает базу в папке {@code data} и запускает консольный интерфейс
 */
public class Main {

    /**
     * Запускает мини-СУБД
     *
     * @param args аргументы командной строки не используются
     */
    public static void main(String[] args) {
        Database db = new Database(new File("data"));

        for (String error : db.getLoadErrors()) {
            System.out.println("ОШИБКА: " + error);
        }

        new DatabaseCli(db).start();
    }
}