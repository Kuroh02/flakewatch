package demo.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/** The kinds of flakiness real teams see. */
class CheckoutTest {

    /** A slow dependency: about 1 call in 6 takes longer than the test is willing to wait. */
    @Test
    void paysWithCard() {
        Duration responseTime = Duration.ofMillis(ThreadLocalRandom.current().nextInt(50, 600));
        assertTrue(responseTime.toMillis() < 520, "payment API answered in " + responseTime.toMillis() + " ms");
    }

    /** A timing race: a background job sometimes hasn't finished when the assertion runs. */
    @Test
    void sendsConfirmationEmail() throws InterruptedException {
        int[] sent = {0};
        Thread mailer = new Thread(() -> sent[0] = 1);
        mailer.start();
        if (ThreadLocalRandom.current().nextInt(10) > 0) {
            mailer.join();
        }
        assertEquals(1, sent[0], "the email was not sent yet");
    }

    @Test
    void calculatesShipping() {
        assertEquals(499, 499);
    }
}
