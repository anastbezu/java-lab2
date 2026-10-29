package org.example;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class TreeIndex implements Serializable {

    private String tableName;
    private String columnName;

    public TreeIndex(String tableName, String columnName) {
        this.tableName = tableName;
        this.columnName = columnName;
    }
    private TreeMap<Comparable, List<Object>> tree = new TreeMap<>();


    public List<Object> get(Object colValue) {

        if (colValue == null) {
            return new ArrayList<>();
        }

        List<Object> list = tree.get((Comparable) colValue);

        if (list==null) {
            return new ArrayList<>();

        }

        return list;

    }



    public void put(Object colValue, Object primaryKey) {

        if (colValue == null) {
            return;
        }

        List<Object> list = tree.computeIfAbsent((Comparable) colValue, k -> new ArrayList<>()); //достаём список по ключу
        list.add(primaryKey);

    }

    public void remove(Object colValue, Object primaryKey) {

        if (colValue == null) {
            return;
        }

        List<Object> list = tree.get((Comparable) colValue);

        if (list==null) {
            return;

        }

        list.remove(primaryKey);

        if (list.isEmpty()) {
        tree.remove((Comparable) colValue);
        }



    }

    public void save(String filePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(this);
        }
    }

    public static TreeIndex load(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            return (TreeIndex) ois.readObject();
        }
    }
}



