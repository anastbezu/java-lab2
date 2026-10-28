# ОБЩИЙ ПЛАН ПРОЕКТА: Упрощённая СУБД на Java

> **Срок выполнения:** 10 дней в комфортном темпе (~1.5–2 часа в день на человека)  
> **Суммарные трудозатраты:** ~12–14 часов чистого времени на каждого  
> **Команда:**  
> • **Ася** — Слой хранения, модели данных, сериализация в файлы и надёжность  
> • **Аня** — SQL-парсер, обработка условий WHERE, валидация и выполнение запросов  
> • **Лиза** — Индексация (TreeMap), консольный интерфейс (CLI), бенчмарки и отчёт  

---

## Главный принцип:

Никаких громоздких «энтерпрайз» библиотек и изобретения велосипедов:
1. **Сериализация на диск** — ровно 3 строчки стандартного Java `Serializable` (`ObjectOutputStream` / `ObjectInputStream`).
2. **Алгоритм индексирования** — используем встроенное сбалансированное дерево `java.util.TreeMap`. Оно уже написано разработчиками Java, даёт поиск за $O(\log N)$ и на 100% закрывает требование ТЗ на максимальный балл.
3. **Парсер запросов** — не используем сложные парсер-генераторы (вроде ANTLR). Запросы по условию гарантированно простые, поэтому используем обычный `split()` и регулярные выражения `Pattern`.
4. **Консольный вывод** — выводим таблицы аккуратным стандартным `System.out.printf("%-10s | %-15s", ...)`.
5. **Бенчмарки** — обычный цикл на 50 000 записей и замер через `System.currentTimeMillis()`.

---

## Предметная область (2 связанные таблицы)

Для выполнения требования о связанных таблицах берём наглядную тему **«Автосалон: Производители и Автомобили»**:

### Таблица 1: `manufacturers` (Производители)
* `id` (`INT`) — **PRIMARY KEY** (уникальный номер завода)
* `name` (`STRING`) — Название компании (например, `'BMW'`, `'Toyota'`)
* `country` (`STRING`) — Страна (например, `'Germany'`, `'Japan'`)
* `is_active` (`BOOLEAN`) — Действует ли завод

### Таблица 2: `cars` (Автомобили)
* `id` (`INT`) — **PRIMARY KEY** (уникальный номер машины)
* `brand` (`STRING`) — Модель/Марка (например, `'M5'`, `'Camry'`)
* `price` (`FLOAT`) — Цена
* `release_date` (`DATE`) — Дата выпуска (формат `YYYY-MM-DD`)
* `in_stock` (`BOOLEAN`) — Есть ли в наличии
* `manufacturer_id` (`INT`) — **FOREIGN KEY** (ссылается на `manufacturers.id`)

*Все 5 требуемых типов данных поддержаны: `INT`, `FLOAT`, `STRING`, `DATE`, `BOOLEAN`.*

---

## Архитектура и поток данных

```mermaid
flowchart TD
    User([Пользователь]) -->|Вводит SQL-запрос| CLI[CLI Консоль\n(Лиза)]
    CLI -->|Строка текста| Parser[SQL Parser & WHERE\n(Аня)]
    Parser -->|Команда на исполнение| Engine[Query Engine\n(Аня)]
    
    Engine -->|Есть индекс по колонке?| IndexManager[Index на базе TreeMap\n(Лиза)]
    IndexManager -.->|Возвращает ID строк| Engine
    
    Engine -->|CRUD операции| Storage[Table & Хранилище\n(Ася)]
    Storage -->|Serializable| Disk[Файлы .db и .idx на диске\n(Ася / Лиза)]
    
    Storage -->|Строки Row| Engine
    Engine -->|QueryResult| CLI
    CLI -->|printf таблица| User
```

---

## Главные контракты (Интерфейсы стыковки)

Все три участницы могут писать код параллельно, опираясь на эти общие классы:

### 1. Результат запроса (Аня ➔ Лиза): `QueryResult.java`
```java
package ru.hse.db.model;

import java.util.List;

public class QueryResult {
    private final boolean success;
    private final String message;
    private final List<String> columns;
    private final List<Row> rows;
    private final int affectedRows;

    public QueryResult(List<String> columns, List<Row> rows) {
        this.success = true;
        this.columns = columns;
        this.rows = rows;
        this.affectedRows = rows.size();
        this.message = rows.size() + " rows in set";
    }

    public QueryResult(int affectedRows, String message) {
        this.success = true;
        this.columns = null;
        this.rows = null;
        this.affectedRows = affectedRows;
        this.message = message;
    }

    public static QueryResult error(String errorMsg) {
        QueryResult r = new QueryResult(0, errorMsg);
        return r;
    }
    // геттеры
}
```

### 2. Методы таблицы (Ася ➔ Аня): `Table.java`
```java
public class Table implements Serializable {
    public TableSchema getSchema() { ... }
    public void insert(Row row) throws DatabaseException { ... }
    public List<Row> select(List<String> columns, Predicate<Row> condition) { ... }
    public int update(Predicate<Row> condition, String column, Object newValue) { ... }
    public int delete(Predicate<Row> condition) { ... }
    public Row getById(Object primaryKey) { ... }
}
```

### 3. Индекс (Лиза ➔ Аня и Ася): `TreeIndex.java`
```java
public class TreeIndex implements Serializable {
    public void put(Object columnValue, Object primaryKey) { ... }
    public List<Object> get(Object columnValue) { ... }
    public void remove(Object columnValue, Object primaryKey) { ... }
}
```

---

## График работ на 10 дней 

| День | Ася (Хранилище & Файлы) | Аня (Парсер & Движок) | Лиза (Индексы & CLI & Отчёт) | Итог дня |
| :---: | :--- | :--- | :--- | :--- |
| **День 1** | Создание репозитория, enum `DataType`, классы `Column` и `Row` | Каркас классов команд (`QueryCommand`, `SelectCommand` и др.) | Каркас `DatabaseCli` (цикл `while (true)` с вводом команд) | Базовый каркас проекта в Git |
| **День 2** | Класс `TableSchema` и базовый класс `Table` на `LinkedHashMap` | Парсинг команды `INSERT INTO ... VALUES (...)` | Вывод результатов в консоль через `printf` с рамками | Базовые кирпичики готовы |
| **День 3** | Проверка уникальности Primary Key и связей Foreign Key при вставке | Парсинг команды `SELECT * FROM table` и `SELECT col1, col2` | Реализация `TreeIndex` на базе `java.util.TreeMap` | Работает логика ключей и индекс |
| **День 4** | Сохранение таблицы в файл и чтение через `Serializable` | Разбор условий `WHERE` (`=, !=, >, <`) в виде `Predicate<Row>` | Метод сохранения `TreeIndex` в файл `.idx` на диск | Файлы пишутся и читаются с диска |
| **День 5** | **ТОЧКА СИНХРОНИЗАЦИИ 1: Сборка базовой СУБД.** Соединяем CLI + Парсер + Таблицы. Проверяем в консоли `INSERT`, `SELECT`, `UPDATE`, `DELETE` без индексов. | Первая полностью рабочая версия программы |
| **День 6** | Подготовка методов обновления индекса при `insert` / `delete` | Парсинг команды `CREATE INDEX idx_name ON table (col);` | Подключение индекса к `Database`: хранение мапы индексов | База умеет создавать индексы |
| **День 7** | Реализация паттерна `Iterator` (обход строк через `Iterable<Row>`) | Подключение оптимизации: если в `SELECT` условие по индексу — ищем через `TreeIndex` | Проверка ускоренного `SELECT` через индекс в консоли | Поиск по индексу работает |
| **День 8** | Защита от сбоев (`ShutdownHook` на экстренное сохранение) | Добавление бонусных команд `CREATE TABLE` и `DROP TABLE` | Написание генератора на 50 000 строк и замер времени (бенчмарк) | Готовы замеры для отчёта и бонусы |
| **День 9** | Паттерн `DAO / Repository` (простая обёртка над файлами) | Обработка ошибок, валидация типов, написание Javadoc | Написание черновика `README.md` (структура, диаграммы Mermaid) | Код закончен, отчёт почти готов |
| **День 10** | **ТОЧКА СИНХРОНИЗАЦИИ 2: Финал.** Совместный прогон всех команд из ТЗ, финальное заполнение `README.md` и сдача! | Проект сдан на высший балл |

---
