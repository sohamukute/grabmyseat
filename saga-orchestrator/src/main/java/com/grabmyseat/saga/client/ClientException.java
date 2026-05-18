package com.grabmyseat.saga.client;

import org.springframework.http.HttpStatusCode;

public class ClientException extends RuntimeException {

    private final HttpStatusCode status;
    private final String body;

    public ClientException(HttpStatusCode status, String body) {
        super("http " + status.value() + ": " + body);
        this.status = status;
        this.body = body;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
