package demo.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A genuinely broken test: it fails every time, so Flakewatch should call it "Broken", not flaky. */
class RefundTest {

    @Test
    void refundsCard() {
        int refunded = 0; // the refund endpoint is not implemented yet
        assertEquals(1999, refunded, "refund amount");
    }
}
