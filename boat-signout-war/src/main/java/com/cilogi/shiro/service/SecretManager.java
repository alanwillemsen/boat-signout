// Minimal Google Secret Manager accessor.
//
// Reads secrets over the Secret Manager REST API using an access token from the
// App Engine metadata server (the app's default service account).  Deliberately
// avoids the heavyweight google-cloud-secretmanager SDK (gRPC/protobuf) so we
// keep this old app's dependency tree small -- it uses the same java.net.http +
// org.json already used elsewhere.
//
// Values are cached after first read, since secrets rarely change within the
// lifetime of an instance.

package com.cilogi.shiro.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONException;
import org.json.JSONObject;

public final class SecretManager {

    private static final String METADATA = "http://metadata.google.internal/computeMetadata/v1";
    private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();

    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * Return the latest value of the given secret, or {@code null} if it can't be read
     * (e.g. running locally with no metadata server).  Cached after the first success.
     */
    public String accessLatest(String secretId) {
        String cached = CACHE.get(secretId);
        if (cached != null) {
            return cached;
        }
        try {
            String value = fetch(secretId);
            CACHE.put(secretId, value);
            return value;
        } catch (IOException | InterruptedException | JSONException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String fetch(String secretId) throws IOException, InterruptedException, JSONException {
        String project = projectId();
        String token = accessToken();
        String url = "https://secretmanager.googleapis.com/v1/projects/" + project
                + "/secrets/" + secretId + "/versions/latest:access";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Secret access failed for " + secretId + ": "
                    + resp.statusCode() + " " + resp.body());
        }
        String data = new JSONObject(resp.body()).getJSONObject("payload").getString("data");
        return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
    }

    private String accessToken() throws IOException, InterruptedException, JSONException {
        HttpResponse<String> resp = metadata("/instance/service-accounts/default/token");
        if (resp.statusCode() != 200) {
            throw new IOException("Metadata token fetch failed: " + resp.statusCode());
        }
        return new JSONObject(resp.body()).getString("access_token");
    }

    private String projectId() throws IOException, InterruptedException {
        HttpResponse<String> resp = metadata("/project/project-id");
        if (resp.statusCode() != 200) {
            throw new IOException("Metadata project-id fetch failed: " + resp.statusCode());
        }
        return resp.body().trim();
    }

    private HttpResponse<String> metadata(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(METADATA + path))
                .header("Metadata-Flavor", "Google")
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
