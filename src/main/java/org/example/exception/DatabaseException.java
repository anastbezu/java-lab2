package org.example.exception;

/**
 * Ошибка СУБД: дубликат первичного ключа, нарушение внешнего ключа, нет таблицы,
 * неверный тип значения и т.д. Текст сообщения можно сразу показывать пользователю.
 */
public class DatabaseException extends Exception {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
