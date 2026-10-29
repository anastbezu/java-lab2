package org.example;

//import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        /*
        //System.out.println("привет");

        //TreeIndex idx = new TreeIndex();

        try {
            // криэйтим индекс и записываем в файл
            TreeIndex idx = new TreeIndex("cars", "brand");
            idx.put("BMW", 1);
            idx.put("Toyota", 2);
            idx.put("BMW", 5);
            idx.save("idx_cars_brand.idx");
            System.out.println("save");

            // читаем обратно
            TreeIndex loaded = TreeIndex.load("idx_cars_brand.idx");
            System.out.println(loaded.get("BMW"));
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }


        //idx.put("BMW", 1);
        //idx.put("Toyota", 2);
        //idx.put("BMW", 5);

        //System.out.println(idx.get("BMW"));
        //System.out.println(idx.get("Audi"));

        //idx.remove("BMW", 1);
        //System.out.println(idx.get("BMW"));
        //idx.remove("BMW", 5);
        //System.out.println(idx.get("BMW"));

         */

         new DatabaseCli().start();

        }

}
