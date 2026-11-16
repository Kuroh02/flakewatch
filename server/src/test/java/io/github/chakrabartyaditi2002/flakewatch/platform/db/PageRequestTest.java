package io.github.chakrabartyaditi2002.flakewatch.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    @DisplayName("US-03 #3: scrolling to the end of 120 tests takes three pages of 50, 50 and 20")
    void pagesThroughAList() {
        PageRequest first = PageRequest.of(null, null);
        assertThat(first.limit()).isEqualTo(50);
        assertThat(first.fetchSize()).isEqualTo(51);

        List<Integer> rows = java.util.stream.IntStream.range(0, 51).boxed().toList();
        PageRequest.Page<Integer> page1 = first.toPage(rows);
        assertThat(page1.items()).hasSize(50);
        assertThat(page1.nextCursor()).isNotNull();

        PageRequest second = PageRequest.of(null, page1.nextCursor());
        assertThat(second.offset()).isEqualTo(50);
        PageRequest third = PageRequest.of(null, second.toPage(rows).nextCursor());
        assertThat(third.offset()).isEqualTo(100);

        PageRequest.Page<Integer> last = third.toPage(rows.subList(0, 20));
        assertThat(last.items()).hasSize(20);
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    @DisplayName("PLAN §12.1: limits outside 1–100 and tampered cursors are a 400")
    void rejectsBadInput() {
        assertThatThrownBy(() -> PageRequest.of(0, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageRequest.of(101, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageRequest.of(null, "bm90LWEtY3Vyc29y")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageRequest.of(null, "%%%")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageRequest.of(null, PageRequest.encode(-5))).isInstanceOf(ApiException.class);
    }
}
