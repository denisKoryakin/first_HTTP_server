package ru.koryakin;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.http.*;
import org.apache.http.client.utils.URLEncodedUtils;

public class Request {
    private final byte[] buffer;
    private final String[] requestLine;
    private final String method;
    private final String uriString;
    private final URI uri;
    private final String protocolVersion;
    private final List<String> headers;
    private final String body;
    private final List<NameValuePair> queryParams;
    private final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
    private final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
    boolean badRequest = false;
    private final int requestLineEnd;
    private final int headersStart;
    private final int headersEnd;
    final List<String> allowedMethods = List.of("GET", "POST");
    private final String contentType;
    private final List<NameValuePair> postParams;

    Request(byte[] buffer) throws IOException, URISyntaxException {
        this.buffer = buffer;
        int read = buffer.length;
        this.requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        this.headersStart = (int) (requestLineEnd + requestLineDelimiter.length);
        this.headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        this.requestLine = requestLineParse();
        this.method = Objects.requireNonNull(requestLineParse())[0];
        this.uriString = Objects.requireNonNull(requestLineParse())[1];
        // даже если присутствует "?" Хэндлер обработает запрос
        if (uriString.contains("?")) {
            this.uri = new URI(uriString.substring(0, uriString.indexOf('?')));
        } else {
            this.uri = new URI(uriString);
        }
        this.queryParams = URLEncodedUtils.parse(uri, Charset.defaultCharset());
        this.protocolVersion = Objects.requireNonNull(requestLineParse())[2];
        this.headers = headersParse();
        this.body = bodyParse();
        if (extractHeader(headers, "Content-Type:").isPresent()) {
            this.contentType = extractHeader(headers, "Content-Type:").get();
        } else {
            this.contentType = "application/x-www-form-urlencoded";
        }
        this.postParams = getPostParams();
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return uri.toString();
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public boolean notValidRequest() {
        if (requestLine.length != 3) {
            badRequest = true;
        }
        return badRequest;
    }

    private String[] requestLineParse() {
        if (requestLineEnd == -1) {
            badRequest = true;
            return null;
        }
        // читаем request line
        final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            badRequest = true;
            return null;
        }
        if (!allowedMethods.contains(requestLine[0])) {
            badRequest = true;
            return null;
        }

        if (!requestLine[1].startsWith("/")) {
            badRequest = true;
            return null;
        }
        return requestLine;
    }

    private List<String> headersParse() throws IOException {
        // ищем заголовки
        if (headersEnd == -1) {
            badRequest = true;
            return null;
        }

        final var headersBytes = Arrays.copyOfRange(buffer, headersStart, headersEnd);
        final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
        return headers;
    }

    private String bodyParse() throws IOException {
        String body = null;
        if (method.equals("GET")) {
            return null;
        } else {
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = extractHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                final var bodyBytes = Arrays.copyOfRange(buffer, headersEnd + headersDelimiter.length, buffer.length);
                body = new String(bodyBytes);
            }
        }
        return body;
    }

    public List<NameValuePair> getQueryParam(String name) {
        if (queryParams.isEmpty()) return null;
        List<NameValuePair> returnedList = null;
        for (NameValuePair param : queryParams) {
            if (name.equals(param.getName())) {
                System.out.println("Query for " + name + " : " + param.getValue());
                returnedList.add(param);
            }
        }
        if (returnedList.isEmpty()) {
            System.out.println("Query not found");
        }
        return returnedList;
    }

    public List<NameValuePair> getQueryParams() {
        for (NameValuePair param : queryParams) {
            System.out.println("Query name: " +
                    param.getName() + ", query param: " +
                    param.getValue());
        }
        return queryParams;
    }

    public List<NameValuePair> getPostParam(String name) {
        if (postParams.isEmpty()) return null;
        List<NameValuePair> returnedList = null;
        for (NameValuePair param : postParams) {
            if (name.equals(param.getName())) {
                System.out.println("POST param for " + name + " : " + param.getValue());
                returnedList.add(param);
            }
        }
        if (returnedList.isEmpty()) {
            System.out.println("POST param not found");
        }
        return returnedList;
    }

    public List<NameValuePair> getPostParams() {
        List<NameValuePair> rawPostParams;
        if (contentType.equals("application/x-www-form-urlencoded")) {
            rawPostParams = URLEncodedUtils.parse(body, Charset.defaultCharset());
            for (NameValuePair param : rawPostParams) {
                System.out.println(param.getName() + ": " + param.getValue());
            }
            return rawPostParams;
        }
        return null;
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
