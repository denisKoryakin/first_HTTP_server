package ru.netology;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    final ExecutorService threadPool = Executors.newFixedThreadPool(64);

    public Server() {
    }

    public void listen(int port) throws IOException {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
            connectionHandle(serverSocket);
        }
    }

    public void connectionHandle(ServerSocket serverSocket) {
        while (true) {
            try {
                final var socket = serverSocket.accept();
                threadPool.submit(new Connection(socket));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
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
                // строка запроса только для чтения для простоты
                // должна быть в форме GET /path HTTP/1.1
                final var requestLine = in.readLine();
                final var parts = requestLine.split(" ");
                final var path = parts[1];
                if (parts.length != 3) {
                    // если путь имеет длину не равную 3 элементам то закрываем сокет
                    socket.close();
                    return;

                }
                if (!validPaths.contains(path)) {
                    out.write((
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.flush();
                    socket.close();
                    return;
                }

                final var filePath = Path.of(".", "public", path);
                final var mimeType = Files.probeContentType(filePath);

                // special case for classic
                if (path.equals("/classic.html")) {
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
                    out.flush();
                    socket.close();
                    return;
                }

                final var length = Files.size(filePath);
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                Files.copy(filePath, out);
                out.flush();
                socket.close();
            } catch (
                    IOException exception) {
                exception.printStackTrace();
            }
        }
    }
}


