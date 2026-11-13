package io.github.chakrabartyaditi2002.flakewatch.platform.error;

import org.springframework.http.HttpStatusCode;

public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;
    private final String detail;

    public ApiException(HttpStatusCode status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public ApiException(HttpStatusCode status, String code, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }
}
