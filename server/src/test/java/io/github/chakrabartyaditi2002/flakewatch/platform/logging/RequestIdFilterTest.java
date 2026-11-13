package io.github.chakrabartyaditi2002.flakewatch.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.platform.random.RandomSource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RandomSource randomSource = RandomSource.seeded(42L);
    private final RequestIdFilter filter = new RequestIdFilter(randomSource);

    @Test
    @DisplayName("NFR-OBS-001: preserves incoming X-Request-Id in MDC and response header")
    void preservesIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcRequestIdDuringFilter = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcRequestIdDuringFilter.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcRequestIdDuringFilter.get()).isEqualTo("req-12345");
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-12345");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("NFR-OBS-001: generates new requestId when X-Request-Id header is missing")
    void generatesRequestIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcRequestIdDuringFilter = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcRequestIdDuringFilter.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcRequestIdDuringFilter.get()).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
            .isEqualTo(mdcRequestIdDuringFilter.get());
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("NFR-OBS-001: clears MDC even when exception occurs in filter chain")
    void clearsMdcOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
        }

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
