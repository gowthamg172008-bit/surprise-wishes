package app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class Main {
    private static final int MAX_REQUEST_BYTES = 16 * 1024 * 1024;
    private static final Set<String> OCCASIONS = Set.of("Birthday", "Wedding", "New Year", "Just because");
    private static final Set<String> THEMES = Set.of("classic", "romantic", "festive", "elegant", "midnight");
    private static final Path ROOT = Path.of(System.getenv().getOrDefault("APP_DIR", ".")).toAbsolutePath().normalize();
    private static final Path DATA = Path.of(
            System.getenv().getOrDefault("DATA_DIR", ROOT.resolve("data").toString())
    ).toAbsolutePath().normalize();

    private Main() {}

    public static void main(String[] args) throws IOException {
        Files.createDirectories(DATA);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String host = System.getenv().getOrDefault("HOST", "0.0.0.0");
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.start();
        System.out.printf("Little Moments is listening on %s:%d with data in %s%n", host, port, DATA);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/health")) {
                if (!exchange.getRequestMethod().equals("GET")) {
                    sendText(exchange, 405, "Method not allowed.", "text/plain; charset=utf-8");
                    return;
                }
                sendText(exchange, 200, "little-moments-ok", "text/plain; charset=utf-8");
                return;
            }
            if (path.equals("/api/wishes")) {
                if (!exchange.getRequestMethod().equals("POST")) {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
                    return;
                }
                createWish(exchange);
                return;
            }
            if (path.startsWith("/api/wishes/")) {
                if (!exchange.getRequestMethod().equals("GET")) {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
                    return;
                }
                getWish(exchange, path.substring("/api/wishes/".length()));
                return;
            }
            serveStatic(exchange, path);
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, jsonError(error.getMessage()));
        } catch (IOException error) {
            System.err.println("Request failed: " + error.getMessage());
            if (exchange.getResponseCode() == -1) sendJson(exchange, 500, "{\"error\":\"The server couldn't complete this request.\"}");
        } catch (Exception error) {
            System.err.println("Unexpected request failure: " + error);
            if (exchange.getResponseCode() == -1) sendJson(exchange, 500, "{\"error\":\"The server couldn't complete this request.\"}");
        } finally {
            exchange.close();
        }
    }

    private static void createWish(HttpExchange exchange) throws IOException {
        byte[] body = readBody(exchange);
        Map<String, String> form = parseForm(new String(body, StandardCharsets.UTF_8));
        String recipient = required(form, "recipient", 60);
        String nickname = optional(form, "nickname", 40);
        String occasion = required(form, "occasion", 30);
        String theme = optional(form, "theme", 20);
        if (theme.isEmpty()) theme = "classic";
        String eventDate = required(form, "eventDate", 10);
        String openDate = required(form, "openDate", 40);
        String thoughts = optional(form, "thoughts", 600);
        String image = optional(form, "image", 8 * 1024 * 1024);

        if (!OCCASIONS.contains(occasion)) throw new IllegalArgumentException("Choose a supported occasion.");
        if (!THEMES.contains(theme)) throw new IllegalArgumentException("Choose a supported wish design.");
        try {
            LocalDate.parse(eventDate);
            Instant.parse(openDate);
        } catch (Exception error) {
            throw new IllegalArgumentException("Enter a valid occasion date and wish opening time.");
        }
        if (!image.isEmpty() && !image.matches("data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/]+={0,2}")) {
            throw new IllegalArgumentException("Use a JPG, PNG, WEBP, or GIF image.");
        }

        String id = UUID.randomUUID().toString();
        Properties wish = new Properties();
        wish.setProperty("recipient", recipient);
        wish.setProperty("nickname", nickname);
        wish.setProperty("occasion", occasion);
        wish.setProperty("theme", theme);
        wish.setProperty("eventDate", eventDate);
        wish.setProperty("openDate", openDate);
        wish.setProperty("thoughts", thoughts);
        wish.setProperty("image", image);
        Path file = DATA.resolve(id + ".properties");
        try (var output = Files.newOutputStream(file)) {
            wish.store(output, "Little Moments wish");
        }
        sendJson(exchange, 201, "{\"id\":\"" + id + "\"}");
    }

    private static void getWish(HttpExchange exchange, String rawId) throws IOException {
        final UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, "{\"error\":\"This wish link is not valid.\"}");
            return;
        }
        Path file = DATA.resolve(id + ".properties").normalize();
        if (!file.getParent().equals(DATA) || !Files.isRegularFile(file)) {
            sendJson(exchange, 404, "{\"error\":\"This wish could not be found.\"}");
            return;
        }
        Properties wish = new Properties();
        try (var input = Files.newInputStream(file)) {
            wish.load(input);
        }
        String json = "{"
                + "\"recipient\":" + jsonString(wish.getProperty("recipient", "")) + ","
                + "\"nickname\":" + jsonString(wish.getProperty("nickname", "")) + ","
                + "\"occasion\":" + jsonString(wish.getProperty("occasion", "")) + ","
                + "\"theme\":" + jsonString(wish.getProperty("theme", "classic")) + ","
                + "\"eventDate\":" + jsonString(wish.getProperty("eventDate", "")) + ","
                + "\"openDate\":" + jsonString(wish.getProperty("openDate", "")) + ","
                + "\"thoughts\":" + jsonString(wish.getProperty("thoughts", "")) + ","
                + "\"image\":" + jsonString(wish.getProperty("image", ""))
                + "}";
        sendJson(exchange, 200, "{\"wish\":" + json + "}");
    }

    private static void serveStatic(HttpExchange exchange, String requestPath) throws IOException {
        if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
            sendText(exchange, 405, "Method not allowed.", "text/plain; charset=utf-8");
            return;
        }
        String name = switch (requestPath) {
            case "/", "/index.html" -> "index.html";
            case "/style.css" -> "style.css";
            case "/app.js" -> "app.js";
            case "/manifest.webmanifest" -> "manifest.webmanifest";
            case "/service-worker.js" -> "service-worker.js";
            case "/icons/icon-192.png" -> "icons/icon-192.png";
            case "/icons/icon-512.png" -> "icons/icon-512.png";
            default -> null;
        };
        if (name == null) {
            sendText(exchange, 404, "Not found.", "text/plain; charset=utf-8");
            return;
        }
        byte[] contents = Files.readAllBytes(ROOT.resolve(name));
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "text/javascript; charset=utf-8"
                : name.endsWith(".webmanifest") ? "application/manifest+json; charset=utf-8"
                : "image/png";
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, contents.length);
            exchange.getResponseBody().write(contents);
        }
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("The wish form has an unsupported content type.");
        }
        try (var input = exchange.getRequestBody(); var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_REQUEST_BYTES) throw new IllegalArgumentException("The uploaded wish is too large.");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new java.util.HashMap<>();
        for (String part : body.split("&")) {
            int separator = part.indexOf('=');
            String key = decode(separator < 0 ? part : part.substring(0, separator));
            String value = decode(separator < 0 ? "" : part.substring(separator + 1));
            values.put(key, value);
        }
        return values;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("The wish form contains invalid encoding.");
        }
    }

    private static String required(Map<String, String> values, String name, int maxLength) {
        String value = values.getOrDefault(name, "").trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException("Check the " + name + " field and try again.");
        }
        return value;
    }

    private static String optional(Map<String, String> values, String name, int maxLength) {
        String value = values.getOrDefault(name, "").trim();
        if (value.length() > maxLength) throw new IllegalArgumentException("The " + name + " field is too long.");
        return value;
    }

    private static String jsonError(String message) {
        return "{\"error\":" + jsonString(message == null ? "The request is invalid." : message) + "}";
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        sendText(exchange, status, body, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
