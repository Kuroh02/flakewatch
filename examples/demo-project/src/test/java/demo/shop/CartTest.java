package demo.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Stable tests: they should always end up "Stable" in Flakewatch. */
class CartTest {

    private final List<Integer> cart = new ArrayList<>();

    @Test
    void addsItem() {
        cart.add(1999);
        assertEquals(1, cart.size());
    }

    @Test
    void removesItem() {
        cart.add(1999);
        cart.remove(0);
        assertTrue(cart.isEmpty());
    }

    @Test
    void sumsPrices() {
        cart.add(1999);
        cart.add(501);
        assertEquals(2500, cart.stream().mapToInt(Integer::intValue).sum());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10})
    void appliesQuantity(int quantity) {
        assertEquals(1999 * quantity, 1999 * quantity);
    }
}
