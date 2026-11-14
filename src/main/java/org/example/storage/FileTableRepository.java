package org.example.storage;

import org.example.exception.DatabaseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранилище таблиц в файлах: каждая таблица сериализуется в свой файл имя.db.
 * Таблица сначала пишется во временный файл .tmp и только потом заменяет старый,
 * поэтому если программа упадёт посреди записи, старый файл не испортится.
 */
public class FileTableRepository implements TableRepository {
    private final File dataDir;

    /**
     * @param dataDir папка с файлами таблиц, создаётся, если её нет
     */
    public FileTableRepository(File dataDir) {
        this.dataDir = dataDir;
        dataDir.mkdirs();
    }

    @Override
    public void save(Table table) throws DatabaseException {
        File file = getFile(table.getName());
        File tempFile = new File(dataDir, table.getName().toLowerCase() + ".db.tmp");
        try {
            try (FileOutputStream fileOut = new FileOutputStream(tempFile);
                 ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
                out.writeObject(table);
            }
            Path from = tempFile.toPath();
            Path to = file.toPath();
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DatabaseException("Не удалось сохранить таблицу '" + table.getName() + "' на диск: "
                    + e.getMessage());
        }
    }

    @Override
    public Table load(String tableName) throws DatabaseException {
        File file = getFile(tableName);
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream fileIn = new FileInputStream(file);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            return (Table) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new DatabaseException("Не получилось прочитать таблицу '" + tableName + "', файл повреждён: "
                    + e.getMessage());
        }
    }

    @Override
    public void delete(String tableName) {
        File file = getFile(tableName);
        file.delete();
    }

    @Override
    public List<String> listTableNames() {
        List<String> names = new ArrayList<>();
        File[] files = dataDir.listFiles();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.endsWith(".db")) {
                String tableName = fileName.substring(0, fileName.length() - 3);
                names.add(tableName);
            }
        }
        return names;
    }

    private File getFile(String tableName) {
        String fileName = tableName.trim().toLowerCase() + ".db";
        return new File(dataDir, fileName);
    }
}
