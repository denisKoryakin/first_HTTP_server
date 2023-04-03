package ru.koryakin;

import java.util.Arrays;

public class Request {
    final String[] requestLine;
    final String method;
    final String path;
    final String protocolVersion;
    final String[] headers;
    final String body;
    final String CRLFx2 = "\r\n\r\n";

    Request(String request) {
        final var partsOfRequest = request.split(CRLFx2);
        if(partsOfRequest.length == 2) {
            this.body = partsOfRequest[1];
        } else {
            this.body = null;
        }
        final var requestLineAndHeaders = partsOfRequest[0].split(" ");
        this.method = requestLineAndHeaders[0];
        this.path = requestLineAndHeaders[1];
        this.protocolVersion = requestLineAndHeaders[2];
        this.requestLine = new String[]{method, path, protocolVersion};
        if (requestLineAndHeaders.length > 3) {
            this.headers = Arrays.copyOfRange(requestLineAndHeaders, 3, requestLineAndHeaders.length);
        } else {
            this.headers = null;
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public String[] getHeaders() {
        return headers;
    }

    public String getBody() {
        return  body;
    }

    public boolean notValidRequest() {
        boolean request = false;
        if (requestLine.length != 3) {
            request = true;
        }
        return request;
    }
}
