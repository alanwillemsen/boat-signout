// Thin client for the bits of the Discord API we need for "Login with Discord".
//
// Two calls:
//   1. exchangeCodeForToken - swap the OAuth authorization code for an access token.
//   2. fetchGuildMember      - look the user up *within the required server*.  This
//      doubles as the membership gate: Discord returns 404 if the user is not in
//      the guild, and on success gives us their server nickname and avatar.

package com.cilogi.shiro.web.discord;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;

public class DiscordClient {

    private static final String API = "https://discord.com/api";

    private final HttpClient http = HttpClient.newHttpClient();

    /** A Discord user as seen within the required server. */
    public static final class GuildMember {
        private final String discordId;
        private final String displayName;
        private final String pictureUrl;

        GuildMember(String discordId, String displayName, String pictureUrl) {
            this.discordId = discordId;
            this.displayName = displayName;
            this.pictureUrl = pictureUrl;
        }

        public String discordId() { return discordId; }
        public String displayName() { return displayName; }
        public String pictureUrl() { return pictureUrl; }
    }

    public String exchangeCodeForToken(String clientId, String clientSecret, String code, String redirectUri)
            throws IOException, InterruptedException, JSONException {
        String form = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri);
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Discord token exchange failed: " + resp.statusCode() + " " + resp.body());
        }
        return new JSONObject(resp.body()).getString("access_token");
    }

    /**
     * Fetch the user's membership in the given server.
     * @return the member, or {@code null} if the user is not in the server.
     */
    public GuildMember fetchGuildMember(String accessToken, String serverId)
            throws IOException, InterruptedException, JSONException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/users/@me/guilds/" + serverId + "/member"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) {
            return null; // not a member of the required server
        }
        if (resp.statusCode() != 200) {
            throw new IOException("Discord guild-member fetch failed: " + resp.statusCode() + " " + resp.body());
        }

        JSONObject member = new JSONObject(resp.body());
        JSONObject user = member.getJSONObject("user");

        String id = user.getString("id");
        String nick = str(member, "nick");
        String globalName = str(user, "global_name");
        String username = str(user, "username");

        // Name precedence: server nickname -> global display name -> username.
        String displayName = firstNonEmpty(nick, globalName, username);

        // Picture: the user's (global) Discord avatar, falling back to Discord's default.
        String avatar = str(user, "avatar");
        String pictureUrl = (avatar != null)
                ? "https://cdn.discordapp.com/avatars/" + id + "/" + avatar + ".png"
                : defaultAvatar(id);

        return new GuildMember(id, displayName, pictureUrl);
    }

    private static String str(JSONObject obj, String key) {
        return obj.isNull(key) ? null : obj.optString(key, null);
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "Discord user";
    }

    private static String defaultAvatar(String id) {
        long index = (Long.parseUnsignedLong(id) >>> 22) % 6;
        return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
