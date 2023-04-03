package ru.koryakin;


import java.io.BufferedOutputStream;
import java.io.IOException;

public class Main {
  public static void main(String[] args) throws IOException {
    final var server = new Server();

    // добавление хендлеров (обработчиков)
    server.addHandler("GET", "/messages", ((request, out) -> sendResponse("Hello from GET /message", out))); /*функц. интерфейс Handler реализован лямбдой*/
    server.addHandler("POST", "/messages", ((request, out) -> sendResponse("Hello from POST /message", out)));

    server.listen(8080);
  }

  public static void sendResponse(String path, BufferedOutputStream out) {
    try {
      out.write((
              "HTTP/1.1 200 OK\r\n" +
                      "Content-Length: " + path.length() + "\r\n" +
                      "Connection: close\r\n" +
                      "\r\n"
      ).getBytes());
      out.write(path.getBytes());
      out.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}


