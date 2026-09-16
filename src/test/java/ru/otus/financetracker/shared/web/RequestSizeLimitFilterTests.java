package ru.otus.financetracker.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import ru.otus.financetracker.configuration.ApplicationProperties;

class RequestSizeLimitFilterTests {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new ApplicationProperties(
            ZoneOffset.UTC,
            new ApplicationProperties.Jwt("https://issuer.test", "finance-tracker-test", "test-signing-secret-with-at-least-32-characters"),
            new ApplicationProperties.Cors(List.of("https://frontend.test")),
            new ApplicationProperties.Limits(DataSize.ofBytes(4), DataSize.ofBytes(4), 1)
    ));

    @Test
    void shouldRejectRequestWhenContentLengthExceedsLimit() throws Exception {
        var request = new MockHttpServletRequest();
        request.setContent(new byte[5]);
        var response = new MockHttpServletResponse();
        var filterChainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> filterChainInvoked.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(filterChainInvoked).isFalse();
    }

    @Test
    void shouldRejectChunkedRequestWhenBodyExceedsLimit() throws Exception {
        var request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent("12345".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrappedRequest, ignoredResponse) ->
                wrappedRequest.getReader().readLine());

        assertThat(response.getStatus()).isEqualTo(413);
    }
}
