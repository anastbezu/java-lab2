package org.example.exception;

/**
 * Ошибка при работе с базой: нет таблицы, повторился первичный ключ, не тот тип значения и т.п.
 * Сообщение написано для пользователя, его можно сразу показывать в консоли.
 */
public class DatabaseException extends Exception {

    /**
     * @param message что пошло не так
     */
    public DatabaseException(String message) {
        super(message);
    }
}
