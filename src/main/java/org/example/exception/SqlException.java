package org.example.exception;

/**
 * Ошибка в тексте SQL-запроса: опечатка, не хватает слова, незакрытая кавычка и т.п.
 * Бросается парсером до того, как запрос дойдёт до базы.
 */
public class SqlException extends RuntimeException {

    /**
     * @param message что не так с запросом
     */
    public SqlException(String message) {
        super(message);
    }
}
