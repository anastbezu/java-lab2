package org.example.storage;

import org.example.exception.DatabaseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранит каждую таблицу в отдельном файле {@code <имя>.db} через стандартную сериализацию Java.
 * <p>
 * Защита от сбоев: таблица сначала пишется во временный файл {@code .tmp}, и только потом
 * он заменяет старый файл. Если программа упадёт во время записи, старый файл останется целым.
 */
public class FileTableRepository implements TableRepository {
    private final File dataDir;

    /**
     * @param dataDir папка, где лежат файлы таблиц (создаётся, если её нет)
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
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DatabaseException("Не удалось сохранить таблицу '" + table.getName() + "': " + e.getMessage());
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
            throw new DatabaseException("Не удалось прочитать таблицу '" + tableName + "': " + e.getMessage());
        }
    }

    @Override
    public void delete(String tableName) {
        getFile(tableName).delete();
    }

    @Override
    public List<String> listTableNames() {
        List<String> names = new ArrayList<>();
        File[] files = dataDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".db")) {
                    names.add(name.substring(0, name.length() - ".db".length()));
                }
            }
        }
        return names;
    }

    private File getFile(String tableName) {
        return new File(dataDir, tableName.trim().toLowerCase() + ".db");
    }
}
