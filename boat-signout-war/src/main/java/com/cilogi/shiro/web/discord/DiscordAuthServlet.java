// "Login with Discord" endpoints.
//
//   GET /discord/login    -> redirect the browser to Discord's OAuth consent screen.
//   GET /discord/callback -> Discord redirects back here with a code.  We exchange it
//                            for a token, verify the user is in the required server,
//                            upsert a GaeUser, and log them in via Shiro.
//
// The existing username/password login is untouched; this just adds a second way in.

package com.cilogi.shiro.web.discord;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;

import com.cilogi.shiro.gae.DiscordAuthenticationToken;
import com.cilogi.shiro.gae.GaeUser;
import com.cilogi.shiro.gae.GaeUserDAO;
import com.cilogi.shiro.service.SecretManager;
import com.cilogi.shiro.web.BaseServlet;


@Singleton
public class DiscordAuthServlet extends BaseServlet {
    static final Logger LOG = Logger.getLogger(DiscordAuthServlet.class.getName());

    private static final String AUTHORIZE_URL = "https://discord.com/api/oauth2/authorize";
    private static final String SCOPE = "identify guilds.members.read";
    private static final String STATE_ATTR = "discord_oauth_state";

    // appengine-web.xml <system-properties>
    private static final String P_CLIENT_ID = "discord.clientId";
    private static final String P_CLIENT_SECRET = "discord.clientSecret";
    private static final String P_SERVER_ID = "discord.serverId";

    // The client secret lives in Google Secret Manager, not in source control.
    private static final String SECRET_ID = "discord-client-secret";

    private final DiscordClient client = new DiscordClient();
    private final SecretManager secretManager = new SecretManager();

    @Inject
    DiscordAuthServlet(Provider<GaeUserDAO> daoProvider) {
        super(daoProvider);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getRequestURI().endsWith("/callback")) {
            handleCallback(request, response);
        } else {
            handleLogin(request, response);
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String clientId = System.getProperty(P_CLIENT_ID);
        if (clientId == null || clientId.isEmpty()) {
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_INTERNAL_SERVER_ERROR,
                    "Discord login is not configured (" + P_CLIENT_ID + " missing)", response);
            return;
        }

        // CSRF state, stashed in the session and checked on the way back.
        String state = new SecureRandomNumberGenerator().nextBytes().toHex();
        SecurityUtils.getSubject().getSession().setAttribute(STATE_ATTR, state);

        String url = AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state)
                + "&redirect_uri=" + enc(redirectUri(request));
        response.sendRedirect(url);
    }

    private void handleCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // User declined consent, or Discord returned an error.
        if (request.getParameter("error") != null) {
            response.sendRedirect("/login.ftl");
            return;
        }

        Session session = SecurityUtils.getSubject().getSession();
        String expectedState = (String) session.getAttribute(STATE_ATTR);
        session.removeAttribute(STATE_ATTR);

        String state = request.getParameter("state");
        String code = request.getParameter("code");
        if (state == null || !state.equals(expectedState)) {
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_FORBIDDEN, "Invalid OAuth state", response);
            return;
        }
        if (code == null || code.isEmpty()) {
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_FORBIDDEN, "Missing OAuth code", response);
            return;
        }

        String clientId = System.getProperty(P_CLIENT_ID);
        String clientSecret = clientSecret();
        String serverId = System.getProperty(P_SERVER_ID);
        if (clientSecret == null || clientSecret.isEmpty()) {
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_INTERNAL_SERVER_ERROR,
                    "Discord login is not configured (client secret unavailable)", response);
            return;
        }

        try {
            String accessToken = client.exchangeCodeForToken(clientId, clientSecret, code, redirectUri(request));
            DiscordClient.GuildMember member = client.fetchGuildMember(accessToken, serverId);
            if (member == null) {
                denyNotMember(response);
                return;
            }

            GaeUserDAO dao = daoProvider.get();
            String principal = GaeUser.discordPrincipal(member.discordId());
            // NB: GaeUserDAO.get()/findUser() never returns null -- for a missing id it
            // returns a fresh, *unregistered* phantom instance.  So detect "already exists"
            // by whether we got back a real, registered account, not by a null check.
            GaeUser found = dao.findUser(principal);
            boolean exists = found != null && found.isRegistered();
            GaeUser user;
            if (exists) {
                // Refresh name/avatar in case the server nickname or avatar changed.
                found.setDisplayName(member.displayName());
                found.setPictureUrl(member.pictureUrl());
                user = found;
            } else {
                user = GaeUser.discordUser(member.discordId(), member.displayName(), member.pictureUrl());
            }
            dao.saveUser(user, !exists);

            Subject subject = SecurityUtils.getSubject();
            loginWithNewSession(new DiscordAuthenticationToken(principal), subject);

            response.sendRedirect("/");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_INTERNAL_SERVER_ERROR, "Discord login interrupted", response);
        } catch (Exception e) {
            LOG.warning("Discord login failed: " + e);
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_INTERNAL_SERVER_ERROR,
                    "Discord login failed: " + e.getMessage(), response);
        }
    }

    private void denyNotMember(HttpServletResponse response) throws IOException {
        String html = "<!DOCTYPE html><html><head><title>Access denied</title></head><body>"
                + "<h1>Not a member</h1>"
                + "<p>Your Discord account is not a member of the required server, so you can't sign in this way.</p>"
                + "<p><a href=\"/login.ftl\">Back to login</a></p>"
                + "</body></html>";
        issue(MIME_TEXT_HTML, HTTP_STATUS_FORBIDDEN, html, response);
    }

    /**
     * The OAuth redirect URI for this deployment, e.g.
     * https://crcboats-dev.uc.r.appspot.com/discord/callback.  Must exactly match a
     * redirect registered in the Discord developer portal.
     */
    private static String redirectUri(HttpServletRequest request) {
        String host = request.getServerName();
        boolean local = "localhost".equals(host) || host.startsWith("127.");
        String scheme = local ? "http" : "https";
        String port = (local && request.getServerPort() != 80 && request.getServerPort() != 443)
                ? ":" + request.getServerPort() : "";
        return scheme + "://" + host + port + "/discord/callback";
    }

    /**
     * The Discord client secret.  Prefers a system property (handy for local dev,
     * where there's no metadata server), otherwise reads it from Secret Manager.
     */
    private String clientSecret() {
        String fromProperty = System.getProperty(P_CLIENT_SECRET);
        if (fromProperty != null && !fromProperty.isEmpty()) {
            return fromProperty;
        }
        return secretManager.accessLatest(SECRET_ID);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
