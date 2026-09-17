package ru.otus.financetracker.shared.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.otus.financetracker.configuration.ApplicationProperties;
import ru.otus.financetracker.shared.ErrorCode;

public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxRequestSizeBytes;
    private final ApiErrorResponseWriter errorResponseWriter;

    public RequestSizeLimitFilter(ApplicationProperties properties, ApiErrorResponseWriter errorResponseWriter) {
        this.maxRequestSizeBytes = properties.limits().maxRequestSize().toBytes();
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxRequestSizeBytes) {
            errorResponseWriter.write(response, request, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    ErrorCode.PAYLOAD_TOO_LARGE, "Request body exceeds the allowed size.");
            return;
        }

        try {
            filterChain.doFilter(new SizeLimitedRequest(request, maxRequestSizeBytes), response);
        } catch (RequestSizeExceededException exception) {
            errorResponseWriter.write(response, request, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    ErrorCode.PAYLOAD_TOO_LARGE, "Request body exceeds the allowed size.");
        }
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private final long maxRequestSizeBytes;

        private SizeLimitedRequest(HttpServletRequest request, long maxRequestSizeBytes) {
            super(request);
            this.maxRequestSizeBytes = maxRequestSizeBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new SizeLimitedServletInputStream(super.getInputStream(), maxRequestSizeBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), requestCharset()));
        }

        private Charset requestCharset() {
            String encoding = getCharacterEncoding();
            if (encoding == null) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (RuntimeException exception) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    private static final class SizeLimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxRequestSizeBytes;
        private long bytesRead;

        private SizeLimitedServletInputStream(ServletInputStream delegate, long maxRequestSizeBytes) {
            this.delegate = delegate;
            this.maxRequestSizeBytes = maxRequestSizeBytes;
        }

        @Override
        public int read() throws IOException {
            int nextByte = delegate.read();
            if (nextByte != -1) {
                checkSize(1);
            }
            return nextByte;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = delegate.read(bytes, offset, length);
            if (count > 0) {
                checkSize(count);
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void checkSize(int readCount) throws RequestSizeExceededException {
            bytesRead += readCount;
            if (bytesRead > maxRequestSizeBytes) {
                throw new RequestSizeExceededException();
            }
        }
    }

}
