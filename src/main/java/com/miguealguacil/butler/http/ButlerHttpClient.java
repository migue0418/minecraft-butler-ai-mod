package com.miguealguacil.butler.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miguealguacil.butler.action.ButlerAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ButlerHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("butler-http");

    private static final String BASE_URL = "http://localhost:8000";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "ChangeMe123!";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static final Gson GSON = new Gson();
    private static String cachedToken = null;

    private ButlerHttpClient() {}

    public static final class AuthException extends RuntimeException {
        AuthException() { super("Auth failed"); }
    }

    public static final class ServerException extends RuntimeException {
        private final int status;
        ServerException(int status) { super("Server error: " + status); this.status = status; }
        public int status() { return status; }
    }

    private static CompletableFuture<String> loginAsync() {
        JsonObject body = new JsonObject();
        body.addProperty("username", USERNAME);
        body.addProperty("password", PASSWORD);
        body.addProperty("remember_me", false);
        String bodyStr = body.toString();
        LOGGER.info("Butler login body: {}", bodyStr);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        LOGGER.error("Butler login failed. Status: {} Body: {}", resp.statusCode(), resp.body());
                        throw new AuthException();
                    }
                    String token = GSON.fromJson(resp.body(), JsonObject.class)
                            .get("access_token").getAsString();
                    cachedToken = token;
                    return token;
                });
    }

    private static CompletableFuture<List<ButlerAction>> askAsync(String message, String token) {
        JsonObject body = new JsonObject();
        body.addProperty("message", message);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/butler/ask"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() == 401) throw new AuthException();
                    if (resp.statusCode() != 200) {
                        LOGGER.error("Butler ask failed. Status: {} Body: {}", resp.statusCode(), resp.body());
                        throw new ServerException(resp.statusCode());
                    }
                    return parseActions(resp.body());
                });
    }

    public static CompletableFuture<List<ButlerAction>> sendAsync(String message) {
        if (cachedToken == null) {
            return loginAsync().thenCompose(token -> askAsync(message, token));
        }
        return askAsync(message, cachedToken)
                .exceptionallyCompose(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof AuthException) {
                        cachedToken = null;
                        return loginAsync().thenCompose(token -> askAsync(message, token));
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    public static CompletableFuture<List<ButlerAction>> sendVoiceAsync(byte[] wavBytes) {
        if (cachedToken == null) {
            return loginAsync().thenCompose(token -> askVoiceAsync(wavBytes, token));
        }
        return askVoiceAsync(wavBytes, cachedToken)
                .exceptionallyCompose(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof AuthException) {
                        cachedToken = null;
                        return loginAsync().thenCompose(token -> askVoiceAsync(wavBytes, token));
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    private static CompletableFuture<List<ButlerAction>> askVoiceAsync(byte[] wavBytes, String token) {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            String partHeader = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"audio\"; filename=\"voice.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n";
            body.write(partHeader.getBytes(StandardCharsets.UTF_8));
            body.write(wavBytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/butler/ask-voice"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() == 401) throw new AuthException();
                    if (resp.statusCode() != 200) {
                        LOGGER.error("Butler ask-voice failed. Status: {} Body: {}", resp.statusCode(), resp.body());
                        throw new ServerException(resp.statusCode());
                    }
                    return parseActions(resp.body());
                });
    }

    private static List<ButlerAction> parseActions(String json) {
        List<ButlerAction> actions = new ArrayList<>();
        for (JsonElement el : GSON.fromJson(json, JsonArray.class)) {
            JsonObject obj = el.getAsJsonObject();
            actions.add(new ButlerAction(
                    obj.get("type").getAsString(),
                    obj.get("message").getAsString(),
                    obj.has("x") && !obj.get("x").isJsonNull() ? obj.get("x").getAsInt() : null,
                    obj.has("y") && !obj.get("y").isJsonNull() ? obj.get("y").getAsInt() : null,
                    obj.has("z") && !obj.get("z").isJsonNull() ? obj.get("z").getAsInt() : null));
        }
        return actions;
    }
}
