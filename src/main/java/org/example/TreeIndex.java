package org.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Индекс по одному столбцу таблицы на основе {@link TreeMap}
 * Хранит соответствие «значение столбца → первичные ключи строк с этим значением»
 * Поиск, вставка и удаление выполняются за O(log N), диапазонные запросы используют {@code tailMap} и {@code headMap}
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TreeIndex implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tableName;
    private final String columnName;
    private final TreeMap<Comparable, List<Object>> tree = new TreeMap<>();

    /**
     * Создаёт пустой индекс
     *
     * @param tableName  имя таблицы
     * @param columnName имя индексируемого столбца
     */
    public TreeIndex(String tableName, String columnName) {
        this.tableName = tableName;
        this.columnName = columnName;
    }

    /**
     * Возвращает первичные ключи строк с указанным значением столбца
     *
     * @param colValue значение столбца
     * @return список ключей; пустой, если таких строк нет или значение равно null
     */
    public List<Object> get(Object colValue) {
        if (colValue == null) {
            return new ArrayList<>();
        }
        List<Object> keys = tree.get((Comparable) colValue);
        return keys == null ? new ArrayList<>() : keys;
    }

    /**
     * Добавляет в индекс строку, значения null не индексируются
     *
     * @param colValue   значение индексируемого столбца
     * @param primaryKey первичный ключ строки
     */
    public void put(Object colValue, Object primaryKey) {
        if (colValue == null) {
            return;
        }
        tree.computeIfAbsent((Comparable) colValue, k -> new ArrayList<>()).add(primaryKey);
    }

    /**
     * Удаляет строку из индекса, если для значения не осталось строк, запись удаляется целиком
     *
     * @param colValue   значение индексируемого столбца
     * @param primaryKey первичный ключ строки
     */
    public void remove(Object colValue, Object primaryKey) {
        if (colValue == null) {
            return;
        }
        List<Object> keys = tree.get((Comparable) colValue);
        if (keys == null) {
            return;
        }
        keys.remove(primaryKey);
        if (keys.isEmpty()) {
            tree.remove((Comparable) colValue);
        }
    }

    /**
     * Сохраняет индекс в файл
     *
     * @param filePath путь к файлу
     * @throws IOException если запись не удалась
     */
    public void save(String filePath) throws IOException {
        try (java.io.FileOutputStream fileOut = new java.io.FileOutputStream(filePath);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(this);
        }
    }

    /**
     * Загружает индекс из файла
     *
     * @param filePath путь к файлу
     * @return загруженный индекс
     * @throws IOException если файл не читается
     * @throws ClassNotFoundException если файл содержит данные неизвестного класса
     */
    public static TreeIndex load(String filePath) throws IOException, ClassNotFoundException {
        try (java.io.FileInputStream fileIn = new java.io.FileInputStream(filePath);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            return (TreeIndex) in.readObject();
        }
    }

    /**
     * Ключи строк, у которых значение строго больше заданного
     */
    public List<Object> greaterThan(Comparable value) {
        return value == null ? new ArrayList<>() : collect(tree.tailMap(value, false));
    }

    /**
     * Ключи строк, у которых значение строго меньше заданного
     */
    public List<Object> lessThan(Comparable value) {
        return value == null ? new ArrayList<>() : collect(tree.headMap(value, false));
    }

    /**
     * Ключи строк, у которых значение больше заданного или равно ему
     */
    public List<Object> greaterOrEqual(Comparable value) {
        return value == null ? new ArrayList<>() : collect(tree.tailMap(value, true));
    }

    /**
     * Ключи строк, у которых значение меньше заданного или равно ему
     */
    public List<Object> lessOrEqual(Comparable value) {
        return value == null ? new ArrayList<>() : collect(tree.headMap(value, true));
    }

    private List<Object> collect(Map<Comparable, List<Object>> part) {
        List<Object> result = new ArrayList<>();
        for (List<Object> keys : part.values()) {
            result.addAll(keys);
        }
        return result;
    }

}



