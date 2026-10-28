# ИНДИВИДУАЛЬНЫЙ ПЛАН: АСЯ (Хранилище, модели данных и файлы)

> **Твоя роль:** Архитектор слоя данных (Data & Storage Layer).  
> **Срок работы:** 10 дней в спокойном темпе (~1–1.5 часа в день).  
> **Главная цель:** Сделать классы для хранения данных (`Row`, `Table`), проверку ключей (PK и FK), надёжное сохранение в файлы на диске и обратно.

---

## Понятный ликбез: всё, что нужно знать про базы данных

Если ты пропустила лекции — не переживай. Вся база данных на нашем уровне — это обычные коллекции Java:

1. **Таблица (`Table`)** — это просто коллекция строк в оперативной памяти: `Map<Object, Row> rows = new LinkedHashMap<>()`. Ключом является `id` строки.
2. **Строка (`Row`)** — это объект с картой `Map<String, Object> values` (например, `"id" -> 1`, `"brand" -> "Toyota"`, `"price" -> 25000.0`).
3. **Первичный ключ (Primary Key, PK)** — это колонка `id`. Главное правило: **значения не могут повторяться**. Если пытаются вставить строку с `id = 1`, а он уже есть в `rows`, выбрасываем ошибку: `throw new DatabaseException("Ключ уже занят!")`.
4. **Внешний ключ (Foreign Key, FK)** — в таблице `cars` колонка `manufacturer_id` указывает на завод. Перед добавлением машины проверяем: есть ли в таблице `manufacturers` строка с таким `id`.
5. **Сериализация на диск** — чтобы данные не стирались при закрытии программы, мы сохраняем объект `Table` в файл через `ObjectOutputStream`. Это стандартная фича Java в 3 строчки кода.

---

## Твой 10-дневный план по шагам

### Дни 1–2: Базовые модели (Типы, Колонка, Строка, Схема)
* [ ] Создать enum `DataType`:
  ```java
  public enum DataType {
      INT, FLOAT, STRING, DATE, BOOLEAN;

      public Object parse(String val) {
          if (val == null || val.equalsIgnoreCase("null")) return null;
          val = val.trim();
          switch (this) {
              case INT: return Integer.parseInt(val);
              case FLOAT: return Double.parseDouble(val);
              case STRING: 
                  if (val.startsWith("'") && val.endsWith("'")) {
                      return val.substring(1, val.length() - 1);
                  }
                  return val;
              case DATE: return java.time.LocalDate.parse(val);
              case BOOLEAN: return Boolean.parseBoolean(val);
              default: return val;
          }
      }
  }
  ```
* [ ] Создать класс `Column`:
  * Поля: `String name`, `DataType type`, `boolean isPrimaryKey`.
* [ ] Создать класс `Row implements Serializable`:
  * Поле: `Map<String, Object> values = new HashMap<>()`.
  * Методы: `Object get(String col)`, `void set(String col, Object val)`.
* [ ] Создать класс `TableSchema implements Serializable`:
  * Список колонок: `List<Column> columns`.
  * Метод `String getPrimaryKeyColumnName()`.

---

### Дни 3–4: Таблица, проверка ключей и сохранение на диск
* [ ] Создать класс `Table implements Serializable`:
  ```java
  public class Table implements Serializable {
      private String name;
      private TableSchema schema;
      // Храним строки по первичному ключу (PK):
      private Map<Object, Row> rows = new LinkedHashMap<>();

      public void insert(Row row) throws DatabaseException {
          String pkCol = schema.getPrimaryKeyColumnName();
          Object pkValue = row.get(pkCol);
          
          if (pkValue == null) {
              throw new DatabaseException("Primary Key не может быть null!");
          }
          if (rows.containsKey(pkValue)) {
              throw new DatabaseException("Запись с Primary Key = " + pkValue + " уже существует!");
          }
          rows.put(pkValue, row);
      }
      
      public List<Row> getAllRows() {
          return new ArrayList<>(rows.values());
      }
      
      public Row getById(Object pk) {
          return rows.get(pk);
      }
  }
  ```
* [ ] Реализовать простую сериализацию в класс `StorageManager`:
  ```java
  public class StorageManager {
      public static void saveTable(Table table, String filePath) throws IOException {
          try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
              oos.writeObject(table);
          }
      }

      public static Table loadTable(String filePath) throws Exception {
          File f = new File(filePath);
          if (!f.exists()) return null;
          try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
              return (Table) ois.readObject();
          }
      }
  }
  ```

---

### День 5: Точка синхронизации №1 (Сборка с Аней и Лизой)
* [ ] Добавить в `Table` методы выполнения операций:
  * `select(List<String> cols, Predicate<Row> filter)`: перебрать `rows.values()`, если `filter.test(row)` верен — вернуть строку.
  * `update(Predicate<Row> filter, String col, Object newVal)`: обновить значение у подходящих строк.
  * `delete(Predicate<Row> filter)`: удалить подходящие строки.
* [ ] Запустить СУБД вместе с CLI Лизы и парсером Ани. Проверить команды `INSERT` и `SELECT *`!

---

### Дни 6–7: Связка с индексом Лизы и паттерн Iterator
* [ ] **Паттерн Iterator (требование ТЗ):**
  * Сделай так, чтобы по таблице можно было пробегать циклом `for (Row r : table)`:
    ```java
    public class Table implements Serializable, Iterable<Row> {
        ...
        @Override
        public Iterator<Row> iterator() {
            return rows.values().iterator();
        }
    }
    ```
  * Преподаватель спросит: «Где у вас Iterator?». Ты покажешь этот метод!
* [ ] **Связка с Индексом:**
  * Добавить в таблицу ссылку на список индексов: когда происходит `insert(row)`, вызываем `index.put(row.get(col), pk)`. При `delete` вызываем `index.remove(...)`.

---

### День 8: Надёжность и защита от сбоев
* [ ] **Защита от сбоев (ShutdownHook):**
  * В классе `Database` добавь хук при запуске:
    ```java
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("\n[СУБД] Сохранение данных перед выходом...");
        saveAllTablesToDisk();
    }));
    ```
  * Если кто-то нажмет `Ctrl+C` в терминале, база успеет сохранить файлы на диск и ничего не потеряется.

---

### День 9: Паттерн DAO / Repository и Javadoc
* [ ] **Паттерн DAO / Repository (требование ТЗ):**
  * Создай интерфейс `TableRepository`:
    ```java
    public interface TableRepository {
        void save(Table table);
        Table load(String tableName);
    }
    ```
  * Сделай класс `FileTableRepository implements TableRepository` и перенеси туда методы работы с файлами.
* [ ] Дописать комментарии Javadoc ко всем своим классам.

---

### День 10: Финал и защита
* [ ] Прогнать сценарии с повторным первичным ключом — убедиться, что система красиво ругается и не падает.
* [ ] Проверить, что после перезапуска программы все вставленные данные на месте.
* [ ] Заполнить блок «Область ответственности Аси» в `README.md`.
