package org.example.sql;

import org.example.exception.SqlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты парсера: какие команды получаются из запросов и какие запросы отклоняются.
 */
public class SqlParserTest {
    private final SqlParser parser = new SqlParser();

    /**
     * Возвращает true, если парсер отказался разбирать запрос.
     */
    private boolean isRejected(String sql) {
        try {
            parser.parse(sql);
        } catch (SqlException e) {
            return true;
        }
        return false;
    }

    @Test
    public void testSelect() {
        QueryCommand command = parser.parse("SELECT * FROM cars");
        assertTrue(command instanceof SelectCommand);
    }

    @Test
    public void testSelectWithWhere() {
        QueryCommand command = parser.parse("SELECT id, brand FROM cars WHERE price > 10000;");
        assertTrue(command instanceof SelectCommand);
    }

    @Test
    public void testInsert() {
        QueryCommand command = parser.parse("INSERT INTO cars VALUES (1, 'Toyota')");
        assertTrue(command instanceof InsertCommand);
    }

    @Test
    public void testInsertWithCommaInString() {
        QueryCommand command = parser.parse("INSERT INTO cars (id, brand) VALUES (1, 'Toyota, Camry')");
        assertTrue(command instanceof InsertCommand);
    }

    @Test
    public void testInsertSeveralRows() {
        QueryCommand command = parser.parse("INSERT INTO cars VALUES (1, 'Toyota'), (2, 'BMW')");
        assertTrue(command instanceof InsertCommand);
    }

    @Test
    public void testUpdate() {
        QueryCommand command = parser.parse("UPDATE cars SET price = 30000 WHERE id = 1");
        assertTrue(command instanceof UpdateCommand);
    }

    @Test
    public void testDelete() {
        QueryCommand command = parser.parse("DELETE FROM cars WHERE id = 1");
        assertTrue(command instanceof DeleteCommand);
    }

    @Test
    public void testCreateIndex() {
        QueryCommand command = parser.parse("CREATE INDEX idx_brand ON cars (brand)");
        assertTrue(command instanceof CreateIndexCommand);
    }

    @Test
    public void testCreateTable() {
        QueryCommand command = parser.parse("CREATE TABLE cars (id INT PRIMARY KEY, brand STRING)");
        assertTrue(command instanceof CreateTableCommand);
    }

    @Test
    public void testCreateTableWithForeignKey() {
        String sql = "CREATE TABLE cars (id INT PRIMARY KEY, manufacturer_id INT REFERENCES manufacturers(id))";
        QueryCommand command = parser.parse(sql);
        assertTrue(command instanceof CreateTableCommand);
    }

    @Test
    public void testDropTable() {
        QueryCommand command = parser.parse("DROP TABLE cars");
        assertTrue(command instanceof DropTableCommand);
    }

    @Test
    public void testWhereWithSpaceInString() {
        String[] parts = SqlParser.parseConditionParts("brand = 'Toyota Camry'");
        String[] expected = {"brand", "=", "'Toyota Camry'"};
        assertArrayEquals(expected, parts);
    }

    @Test
    public void testWhereWithNumber() {
        String[] parts = SqlParser.parseConditionParts("price != 30000.0");
        String[] expected = {"price", "!=", "30000.0"};
        assertArrayEquals(expected, parts);
    }

    @Test
    public void testWhereWithGreaterOrEqual() {
        String[] parts = SqlParser.parseConditionParts("price >= 20000");
        String[] expected = {"price", ">=", "20000"};
        assertArrayEquals(expected, parts);
    }

    @Test
    public void testWhereWithLessOrEqual() {
        String[] parts = SqlParser.parseConditionParts("price<=20000");
        String[] expected = {"price", "<=", "20000"};
        assertArrayEquals(expected, parts);
    }

    @Test
    public void testUnknownCommandRejected() {
        assertTrue(isRejected("UNKNOWN COMMAND"));
    }

    @Test
    public void testSelectWithoutFromRejected() {
        assertTrue(isRejected("SELECT * cars"));
    }

    @Test
    public void testUnclosedQuoteRejected() {
        assertTrue(isRejected("INSERT INTO cars VALUES (1, 'Toyota)"));
    }

    @Test
    public void testUnsupportedSyntaxRejected() {
        assertTrue(isRejected("SELECT * FROM cars WHERE id = 1 AND price > 5"));
        assertTrue(isRejected("UPDATE cars SET price = 1, brand = 'x' WHERE id = 1"));
        assertTrue(isRejected("DELETE FROM cars"));
    }
}
