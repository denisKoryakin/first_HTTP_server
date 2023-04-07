package ru.koryakin;

import java.io.*;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;

class Connection extends Thread {

    private final Socket socket;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Handler>> handlersList;

    Connection(Socket socket, ConcurrentHashMap<String, ConcurrentHashMap<String, Handler>> handlersList) {
        this.socket = socket;
        this.handlersList = handlersList;
    }

    @Override
    public void run() {
        try (final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            // лимит на request line + заголовки
            final var limit = 4096;
            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);

            //  парсим новый запрос
            Request request = new Request(buffer);

            //  ищем имеющийся Хэндлер по методу и пути и отдаем обработчику запрос на обработку
            if (handlersList.containsKey(request.getMethod())) {
                handlersList.values().stream()
                        .filter(x -> x.containsKey(request.getPath()))
                        .map(x -> x.get(request.getPath()))
                        .forEach(x -> {
                            try {
                                x.handle(request, out);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } else {
                //  если не нашли, создаем анонимный класс с переопределенным методом handle()
                Handler handler = new Handler() {
                    @Override
                    public void handle(Request request, BufferedOutputStream out) throws IOException {
                        if (!handlersList.get(request.getMethod()).containsKey(request.getPath())) {
                            notFound(out);
                        } else if(request.notValidRequest()) {
                            badRequest(out);
                        }
                    }
                };
                handler.handle(request, out);
            }
            //  закрываем сокет
            socket.close();
        } catch (
                IOException | URISyntaxException exception) {
            exception.printStackTrace();
        }
    }

    void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Not Bad request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    void notFound(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }
}
