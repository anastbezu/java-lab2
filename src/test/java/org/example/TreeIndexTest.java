package org.example;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Тесты индекса TreeIndex: точный и диапазонный поиск, удаление.
 */
public class TreeIndexTest {

    private TreeIndex priceIndex() {
        TreeIndex idx = new TreeIndex("cars", "price");
        idx.put(15000.0, 1);
        idx.put(25000.0, 2);
        idx.put(30000.0, 3);
        return idx;
    }

    @Test
    public void testPutGetRemove() {
        TreeIndex idx = new TreeIndex("cars", "brand");
        idx.put("BMW", 1);
        idx.put("BMW", 5);
        assertEquals(Arrays.asList(1, 5), idx.get("BMW"));
        idx.remove("BMW", 1);
        assertEquals(Arrays.asList(5), idx.get("BMW"));
        idx.remove("BMW", 5);
        assertEquals(Collections.emptyList(), idx.get("BMW"));
    }

    @Test
    public void testNullIsIgnored() {
        TreeIndex idx = new TreeIndex("cars", "brand");
        idx.put(null, 1);
        assertEquals(Collections.emptyList(), idx.get(null));
    }

    @Test
    public void testRanges() {
        TreeIndex idx = priceIndex();
        assertEquals(Arrays.asList(2, 3), idx.greaterThan(20000.0));
        assertEquals(Arrays.asList(1), idx.lessThan(20000.0));
        assertEquals(Arrays.asList(2, 3), idx.greaterOrEqual(25000.0));
        assertEquals(Arrays.asList(1, 2), idx.lessOrEqual(25000.0));
    }
}