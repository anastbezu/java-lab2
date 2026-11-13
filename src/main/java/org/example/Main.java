package org.example;
import org.example.storage.Database;
import java.io.File;

//import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Database db = new Database(new File("data"));
        new DatabaseCli(db).start();
    }
}
