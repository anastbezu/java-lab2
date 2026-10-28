# ИНДИВИДУАЛЬНЫЙ ПЛАН: ЛИЗА (Индексация, CLI, бенчмарки и отчёт)

> **Твоя роль:** Инженер производительности и интерфейса (Index Engine, CLI & Performance).  
> **Срок работы:** 10 дней в спокойном темпе (~1–1.5 часа в день).  
> **Главная цель:** Реализовать индекс на базе встроенного `TreeMap` для мгновенного поиска, сделать удобную консоль с выводом красивых таблиц, снять замеры ускорения на 50 000 строк и оформить `README.md`.

---

## Понятный ликбез: индекс по-студенчески

1. **Зачем писать B-дерево с нуля, если в Java уже есть `TreeMap`?**  
   `java.util.TreeMap` — это сбалансированное красно-чёрное дерево, написанное разработчиками Java. Оно гарантирует поиск за $O(\log N)$.
   Всё, что нам нужно — это сделать класс `TreeIndex`, внутри которого лежит:
   ```java
   // Значение колонки -> Список Primary Keys строк
   private TreeMap<Comparable, List<Object>> map = new TreeMap<>();
   ```
   Это на 100% честный и быстрый индекс, полностью удовлетворяющий ТЗ лабораторной.
2. **Как сохранять индекс на диск?**  
   Точно так же, как Ася сохраняет таблицы — просто `implements Serializable` и сохраняем дерево в файл `.idx` через `ObjectOutputStream`.

---

## Твой 10-дневный план по шагам

### Дни 1–2: Консольный цикл (CLI / REPL)
* [ ] Создать класс `DatabaseCli`:
  ```java
  public class DatabaseCli {
      private final SqlParser parser;
      private final Database database;

      public void start() {
          Scanner scanner = new Scanner(System.in);
          System.out.println("=== Mini-DBMS Console (Введите 'exit' для выхода) ===");

          while (true) {
              System.out.print("db> ");
              String line = scanner.nextLine().trim();
              if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                  System.out.println("Выход из СУБД...");
                  break;
              }
              if (line.isEmpty()) continue;

              try {
                  long t1 = System.currentTimeMillis();
                  QueryCommand cmd = parser.parse(line);
                  QueryResult result = cmd.execute(database);
                  long durationMs = System.currentTimeMillis() - t1;

                  printResult(result, durationMs);
              } catch (Exception e) {
                  System.out.println("ОШИБКА: " + e.getMessage());
              }
          }
      }
  }
  ```

---

### Дни 3–4: Вывод результатов в консоль и класс `TreeIndex`
* [ ] **Печать красивой таблицы через `System.out.printf`:**
  ```java
  public void printTable(List<String> cols, List<Row> rows, long durationMs) {
      // 1. Печатаем шапку
      for (String col : cols) {
          System.out.printf("| %-15s ", col);
      }
      System.out.println("|");

      // 2. Линия разделителя
      for (int i = 0; i < cols.size(); i++) {
          System.out.print("+-----------------");
      }
      System.out.println("+");

      // 3. Строки данных
      for (Row r : rows) {
          for (String col : cols) {
              Object val = r.get(col);
              System.out.printf("| %-15s ", val == null ? "NULL" : val.toString());
          }
          System.out.println("|");
      }
      System.out.println("(" + rows.size() + " строк, " + durationMs + " мс)");
  }
  ```
* [ ] **Класс `TreeIndex implements Serializable`:**
  ```java
  public class TreeIndex implements Serializable {
      private String tableName;
      private String columnName;
      private TreeMap<Comparable, List<Object>> tree = new TreeMap<>();

      public void put(Object colValue, Object primaryKey) {
          if (colValue == null) return;
          tree.computeIfAbsent((Comparable) colValue, k -> new ArrayList<>()).add(primaryKey);
      }

      public List<Object> get(Object colValue) {
          return tree.getOrDefault((Comparable) colValue, Collections.emptyList());
      }

      public void remove(Object colValue, Object primaryKey) {
          List<Object> list = tree.get((Comparable) colValue);
          if (list != null) {
              list.remove(primaryKey);
              if (list.isEmpty()) tree.remove((Comparable) colValue);
          }
      }
  }
  ```

---

### День 5: Сборка №1 (Базовая версия без индексов)
* [ ] Запустить CLI вместе с парсером Ани и таблицами Аси.
* [ ] Проверить базовый CRUD в терминале:
  * `INSERT INTO cars VALUES (1, 'Toyota', 25000.0, '2023-01-01', TRUE, 1);`
  * `SELECT * FROM cars;`
  * `UPDATE cars SET price = 27000.0 WHERE id = 1;`
  * `DELETE FROM cars WHERE id = 1;`

---

### Дни 6–7: Интеграция индекса и сохранение на диск
* [ ] Сделать сохранение `TreeIndex` в файл `./data/<index_name>.idx` и автоматическую загрузку при старте.
* [ ] Добавить в `Database` коллекцию индексов: `Map<String, TreeIndex> indexes`.
* [ ] Помочь Ане подключить `index.get(...)` в `SELECT`:
  * Если `WHERE brand = 'BMW'` и есть индекс по `brand`, брать ключи строк из дерева за доли миллисекунды!

---

### День 8: Бенчмарки производительности на 50 000 строк
* [ ] Создать класс `Benchmark`:
  1. Генерируем 50 000 записей машин (случайные марки, цены, даты).
  2. Замеряем время поиска **БЕЗ индекса**:
     ```java
     long t1 = System.currentTimeMillis();
     for (int i = 0; i < 500; i++) {
         table.select(..., row -> "BMW".equals(row.get("brand")));
     }
     long timeNoIndex = System.currentTimeMillis() - t1;
     ```
  3. Создаем индекс: `TreeIndex`.
  4. Замеряем время поиска **С ИНДЕКСОМ**:
     ```java
     long t2 = System.currentTimeMillis();
     for (int i = 0; i < 500; i++) {
         List<Object> pks = index.get("BMW");
         // достаем строки по ID
     }
     long timeWithIndex = System.currentTimeMillis() - t2;
     ```
  5. Записываем полученные цифры (например: без индекса 350 мс, с индексом 6 мс — ускорение в 58 раз!). Это пойдёт в отчёт.

---

### День 9: Оформление `README.md`
* [ ] Написать качественный `README.md`:
  * Диаграмма классов (Class Diagram) в синтаксисе Mermaid.
  * Диаграмма вызова с использованием индекса (Sequence Diagram).
  * Таблица замеров производительности (с индексом vs без).
  * Инструкция пользователя: как собрать (`mvn package`) и запустить программу.
  * Раздел: «Вклад участников» (Ася, Аня, Лиза).
  * Выводы и список источников.

---

### День 10: Финал и сдача
* [ ] Прогнать программу от начала до конца.
* [ ] Проверить все примеры команд из ТЗ лабораторной.
* [ ] Сдать работу!
