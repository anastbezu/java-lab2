package org.example.storage;

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
 * Индекс по одной колонке на основе TreeMap.
 * Для каждого значения колонки хранит первичные ключи строк с этим значением.
 * TreeMap держит значения отсортированными, поэтому кроме поиска по равенству
 * можно быстро искать всё, что больше или меньше заданного значения.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TreeIndex implements Serializable {
    private static final long serialVersionUID = 1L;

    private final TreeMap<Comparable, List<Object>> tree = new TreeMap<>();

    /**
     * Возвращает первичные ключи строк, где колонка равна значению.
     *
     * @param colValue значение колонки
     * @return список ключей, пустой, если таких строк нет или значение NULL
     */
    public List<Object> get(Object colValue) {
        List<Object> result = new ArrayList<>();
        if (colValue == null) {
            return result;
        }
        List<Object> keys = tree.get((Comparable) colValue);
        if (keys != null) {
            result.addAll(keys);
        }
        return result;
    }

    /**
     * Добавляет строку в индекс. NULL не индексируется.
     *
     * @param colValue   значение колонки
     * @param primaryKey первичный ключ строки
     */
    public void put(Object colValue, Object primaryKey) {
        if (colValue == null) {
            return;
        }
        Comparable key = (Comparable) colValue;
        List<Object> keys = tree.get(key);
        if (keys == null) {
            keys = new ArrayList<>();
            tree.put(key, keys);
        }
        keys.add(primaryKey);
    }

    /**
     * Убирает строку из индекса. Если для значения строк не осталось, значение удаляется целиком.
     *
     * @param colValue   значение колонки
     * @param primaryKey первичный ключ строки
     */
    public void remove(Object colValue, Object primaryKey) {
        if (colValue == null) {
            return;
        }
        Comparable key = (Comparable) colValue;
        List<Object> keys = tree.get(key);
        if (keys == null) {
            return;
        }
        keys.remove(primaryKey);
        if (keys.isEmpty()) {
            tree.remove(key);
        }
    }

    /**
     * Ключи строк, где значение строго больше заданного.
     */
    public List<Object> greaterThan(Comparable value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return collect(tree.tailMap(value, false));
    }

    /**
     * Ключи строк, где значение строго меньше заданного.
     */
    public List<Object> lessThan(Comparable value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return collect(tree.headMap(value, false));
    }

    /**
     * Ключи строк, где значение больше заданного или равно ему.
     */
    public List<Object> greaterOrEqual(Comparable value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return collect(tree.tailMap(value, true));
    }

    /**
     * Ключи строк, где значение меньше заданного или равно ему.
     */
    public List<Object> lessOrEqual(Comparable value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return collect(tree.headMap(value, true));
    }

    /**
     * Сохраняет индекс в файл.
     *
     * @throws IOException если записать не получилось
     */
    public void save(String filePath) throws IOException {
        try (FileOutputStream fileOut = new FileOutputStream(filePath);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(this);
        }
    }

    /**
     * Загружает индекс из файла.
     *
     * @throws IOException            если файл не читается
     * @throws ClassNotFoundException если в файле что-то не то
     */
    public static TreeIndex load(String filePath) throws IOException, ClassNotFoundException {
        try (FileInputStream fileIn = new FileInputStream(filePath);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            return (TreeIndex) in.readObject();
        }
    }

    private List<Object> collect(Map<Comparable, List<Object>> part) {
        List<Object> result = new ArrayList<>();
        for (List<Object> keys : part.values()) {
            result.addAll(keys);
        }
        return result;
    }
}
