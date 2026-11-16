package demo.shop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class SearchTest {

    @Test
    void findsShoes() {
        assertTrue("running shoes".contains("shoes"));
    }

    /**
     * Only flaky on the newer Java version, like a test that only breaks in one browser.
     * Flakewatch shows it as flaky on that variant and stable on the other.
     */
    @Test
    void ranksBestSellersFirst() {
        boolean newerJava = Runtime.version().feature() >= 25;
        boolean unlucky = ThreadLocalRandom.current().nextInt(4) == 0;
        assertTrue(!(newerJava && unlucky), "ranking order differed between two identical queries");
    }
}
