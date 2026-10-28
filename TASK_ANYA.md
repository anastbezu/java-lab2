# ИНДИВИДУАЛЬНЫЙ ПЛАН: АНЯ (SQL Парсер и Движок запросов)

> **Твоя роль:** Мозг системы (Query Parser & Engine).  
> **Срок работы:** 10 дней в спокойном темпе (~1–1.5 часа в день).  
> **Главная цель:** Принять строчку текста от Лизы, понять, что от базы хотят (распарсить запрос), вызвать нужные методы у Аси и передать готовый результат обратно Лизе.

---

## Понятный ликбез:

Не нужно подключать тяжелые библиотеки грамматик (ANTLR, JavaCC). По условию лабораторной **все запросы гарантированно простого формата**.  
Поэтому парсинг пишется через обычные методы строк: `startsWith()`, `split()`, `substring()` и простые регулярки `java.util.regex`.

---

## Твой 10-дневный план по шагам

### Дни 1–2: Архитектура команд (Command Pattern)
* [ ] Создать интерфейс `QueryCommand`:
  ```java
  public interface QueryCommand {
      QueryResult execute(Database db) throws Exception;
  }
  ```
* [ ] Создать класс `SqlParser` с маршрутизацией по первому слову:
  ```java
  public class SqlParser {
      public QueryCommand parse(String sql) throws Exception {
          sql = sql.trim().replaceAll(";+$", ""); // убираем точку с запятой в конце
          String upper = sql.toUpperCase();

          if (upper.startsWith("SELECT")) return parseSelect(sql);
          if (upper.startsWith("INSERT INTO")) return parseInsert(sql);
          if (upper.startsWith("UPDATE")) return parseUpdate(sql);
          if (upper.startsWith("DELETE FROM")) return parseDelete(sql);
          if (upper.startsWith("CREATE INDEX")) return parseCreateIndex(sql);
          if (upper.startsWith("CREATE TABLE")) return parseCreateTable(sql);
          if (upper.startsWith("DROP TABLE")) return parseDropTable(sql);

          throw new IllegalArgumentException("Неизвестная команда!");
      }
  }
  ```

---

### Дни 3–4: Парсинг `INSERT` и `SELECT`
* [ ] **Парсинг `INSERT INTO` (2 формата из ТЗ):**
  * `INSERT INTO cars VALUES (1, 'Toyota', 25000.0, '2023-01-01', TRUE, 1)`  
    *Как разобрать:*  
    1. Имя таблицы — слово после `INSERT INTO`.  
    2. Значения — то, что между скобками `(...)`. Разбиваем по запятым `,` с удалением пробелов и кавычек.
  * `INSERT INTO cars (id, brand) VALUES (2, 'BMW')`  
    *Как разобрать:*  
    1. Имена колонок берем из первых скобок.  
    2. Значения — из вторых скобок после `VALUES`.  
    3. Создаем `Row`, незаполненные колонки оставляем `null`.
* [ ] **Парсинг `SELECT` (проекция колонок):**
  * `SELECT * FROM cars` $\rightarrow$ выводим все колонки.
  * `SELECT brand, price FROM cars` $\rightarrow$ выводим только указанные колонки.

---

### День 5: Парсинг `WHERE`, `UPDATE`, `DELETE` и сборка №1
* [ ] **Разбор условий `WHERE`:**  
  В ТЗ условия очень простые: `WHERE col operator value` (например: `WHERE price > 20000`, `WHERE brand = 'Toyota'`).
  * Напиши метод, который возвращает `Predicate<Row>`:
    ```java
    public static Predicate<Row> parseWhere(String whereClause) {
        // Режем по пробелам: "price", ">", "20000"
        String[] parts = whereClause.trim().split("\\s+");
        String col = parts[0];
        String op = parts[1];
        String rawVal = parts[2].replace("'", ""); // убираем кавычки если строка

        return row -> {
            Object cell = row.get(col);
            if (cell == null) return false;
            
            if (op.equals("=")) return cell.toString().equalsIgnoreCase(rawVal);
            if (op.equals("!=")) return !cell.toString().equalsIgnoreCase(rawVal);
            if (op.equals(">")) return Double.parseDouble(cell.toString()) > Double.parseDouble(rawVal);
            if (op.equals("<")) return Double.parseDouble(cell.toString()) < Double.parseDouble(rawVal);
            return false;
        };
    }
    ```
* [ ] **Сборка №1:** Подключи свои команды к таблицам Аси и консоли Лизы. Базовые запросы `INSERT`, `SELECT`, `UPDATE`, `DELETE` должны заработать!

---

### Дни 6–7: Индексы и оптимизатор запросов
* [ ] **Разбор команды `CREATE INDEX`:**
  * Запрос: `CREATE INDEX idx_cars_brand ON cars (brand);`
  * Достаём имя индекса (`idx_cars_brand`), таблицу (`cars`) и колонку (`brand`).
  * Вызываем метод `database.createIndex("cars", "brand", "idx_cars_brand")`.
* [ ] **Оптимизатор запроса (Query Optimizer):**
  * В классе `SelectCommand`:
    Если в запросе есть `WHERE col = 'value'` и по этой колонке `col` у таблицы **есть индекс Лизы**:
    1. Не сканируем всю таблицу!
    2. Достаём `id` строк напрямую из индекса Лизы: `List<Object> pks = index.get("value")`.
    3. Достаем только эти строки по `table.getById(pk)`.
    4. Преподаватель оценит: это реальная оптимизация запроса с использованием индекса!

---

### День 8: Команды `CREATE TABLE` и `DROP TABLE` (Бонус на максимум)
* [ ] Реализовать `CREATE TABLE`:
  * Запрос: `CREATE TABLE cars (id INT PRIMARY KEY, brand STRING, price FLOAT);`
  * Разобрать типы колонок и передать схему в `Database.createTable(...)`.
* [ ] Реализовать `DROP TABLE`:
  * Запрос: `DROP TABLE cars;`
  * Удалить таблицу из памяти и стереть её файл с диска.

---

### Дни 9–10: Полировка, Javadoc и сдача
* [ ] Проверить, чтобы при ошибках в запросе программа не падала, а возвращала понятный `QueryResult.error(...)`.
* [ ] Написать Javadoc для своих классов.
* [ ] Заполнить блок «Область ответственности Ани» в `README.md`.
