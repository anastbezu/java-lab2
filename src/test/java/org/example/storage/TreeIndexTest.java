package org.example.storage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Тесты индекса: поиск по значению, по диапазону и удаление.
 */
public class TreeIndexTest {

    private TreeIndex priceIndex() {
        TreeIndex index = new TreeIndex();
        index.put(15000.0, 1);
        index.put(25000.0, 2);
        index.put(30000.0, 3);
        return index;
    }

    @Test
    public void testPutGetRemove() {
        TreeIndex index = new TreeIndex();
        index.put("BMW", 1);
        index.put("BMW", 5);
        assertEquals(Arrays.asList(1, 5), index.get("BMW"));

        index.remove("BMW", 1);
        assertEquals(Arrays.asList(5), index.get("BMW"));

        index.remove("BMW", 5);
        assertEquals(Collections.emptyList(), index.get("BMW"));
    }

    @Test
    public void testNullIsIgnored() {
        TreeIndex index = new TreeIndex();
        index.put(null, 1);
        assertEquals(Collections.emptyList(), index.get(null));
    }

    @Test
    public void testRanges() {
        TreeIndex index = priceIndex();
        assertEquals(Arrays.asList(2, 3), index.greaterThan(20000.0));
        assertEquals(Arrays.asList(1), index.lessThan(20000.0));
        assertEquals(Arrays.asList(3), index.greaterThan(25000.0));
        assertEquals(Arrays.asList(2, 3), index.greaterOrEqual(25000.0));
        assertEquals(Arrays.asList(1, 2), index.lessOrEqual(25000.0));
    }
}
