package ru.koryakin;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final List<String> filePaths = new ArrayList<>();
    private final CopyOnWriteArrayList<Request> preRequestsList = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Handler>> handlersList = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(64);

    public Server() {
    }

    public void listen(int port) throws IOException {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
//  заполняем список валидных путей
            File dir = new File("public");
            if (dir.isDirectory()) {
                for (File item : dir.listFiles()) {
                    filePaths.add(item.getName());
                }
            }
//  заполняем список предварительных валидных запросов
            for (String filePath : filePaths) {
                preRequestsList.add(new Request("GET " + "/" + filePath + " HTTP/1.1"));
            }
//  заполняем мапу обработчиков handlersList, для которых создаются анонимные классы обработчика
            for (Request request : preRequestsList) {
                addHandler(request.getMethod(), request.getPath(), new Handler() {
                    @Override
                    public void handle(Request request, BufferedOutputStream out) throws IOException {
//  response на classic.html
                        final var filePath = Path.of(".", "public", request.getPath());
                        final var mimeType = Files.probeContentType(filePath);
                        if (request.getPath().equals("/classic.html")) {
                            final var template = Files.readString(filePath);
                            final var content = template.replace(
                                    "{time}",
                                    LocalDateTime.now().toString()
                            ).getBytes();
                            out.write((
                                    "HTTP/1.1 200 OK\r\n" +
                                            "Content-Type: " + mimeType + "\r\n" +
                                            "Content-Length: " + content.length + "\r\n" +
                                            "Connection: close\r\n" +
                                            "\r\n"
                            ).getBytes());
                            out.write(content);
                        } else {
//  response на имеющийся ресурс в папке public
                            final var length = Files.size(filePath);
                            out.write((
                                    "HTTP/1.1 200 OK\r\n" +
                                            "Content-Type: " + mimeType + "\r\n" +
                                            "Content-Length: " + length + "\r\n" +
                                            "Connection: close\r\n" +
                                            "\r\n"
                            ).getBytes());
                            Files.copy(filePath, out);
                        }
                        out.flush();
                    }
                });
            }
//  запуск метода обработки нового подключения
            connectionHandle(serverSocket);
        }
    }

    public void connectionHandle(ServerSocket serverSocket) {
        while (true) {
            try {
                final var socket = serverSocket.accept();
//  запуск потока обработки нового подключения
                threadPool.submit(new Connection(socket));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (handlersList.containsKey(method)) {
            if (!handlersList.get(method).containsKey(path)) {
                handlersList.get(method).put(path, handler);
            }
        } else {
            ConcurrentHashMap<String, Handler> innerHandlersList = new ConcurrentHashMap<>();
            innerHandlersList.put(path, handler);
            handlersList.put(method, innerHandlersList);
        }
    }

    class Connection extends Thread {

        Socket socket;

        Connection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 final var out = new BufferedOutputStream(socket.getOutputStream())) {
//  строка запроса только для чтения для простоты должна быть в форме GET /path HTTP/1.1
//  парсим новый запрос
                Request request = new Request(in.readLine());
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
                            if (!handlersList.get(request.getMethod()).containsKey(request.getPath()) || request.notValidRequest()) {
                                out.write((
                                        "HTTP/1.1 404 Not Found\r\n" +
                                                "Content-Length: 0\r\n" +
                                                "Connection: close\r\n" +
                                                "\r\n"
                                ).getBytes());
                                out.flush();
                            }
                        }
                    };
                    handler.handle(request, out);
                }
//  закрываем сокет
                socket.close();
            } catch (
                    IOException exception) {
                exception.printStackTrace();
            }
        }
    }
}


