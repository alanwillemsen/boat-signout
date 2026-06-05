// Logout endpoint that is safe against browser prefetch/prerender.
//
// The "Sign Out" link is a GET <a href="/logout">. Modern browsers (Chrome's
// "Preload pages", speculation rules, etc.) speculatively fetch such links, and a
// GET that destroys the session would silently log users out mid-session -> login
// loop. We therefore IGNORE speculative requests (identified by the Sec-Purpose /
// Purpose / X-moz headers) and only log out on a genuine user request -- which may
// be a GET (the plain link) or a POST. The 204 for speculative hits is marked
// no-store so the browser won't reuse it for the subsequent real click.

package com.cilogi.shiro.web.user;

import java.io.IOException;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.SecurityUtils;

import com.cilogi.shiro.gae.GaeUserDAO;
import com.cilogi.shiro.web.BaseServlet;


@Singleton
public class LogoutServlet extends BaseServlet {
    static final Logger LOG = Logger.getLogger(LogoutServlet.class.getName());

    @Inject
    LogoutServlet(Provider<GaeUserDAO> daoProvider) {
        super(daoProvider);
    }

    // Logout is a state change, so it MUST be a POST. GET never logs out: browsers
    // prefetch/prerender the "Sign Out" link, and (as observed) mobile Chrome's
    // speculative GET doesn't reliably send Sec-Purpose, so header sniffing can't catch
    // it. Making GET a harmless no-op is the only robust fix. The Sign Out button POSTs
    // (see status.js).
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Only a genuine, user-activated navigation (a real click on the Sign Out link)
        // carries "Sec-Fetch-User: ?1". Browser prefetch/prerender of the link does NOT
        // (confirmed in prod: those arrive with Sec-Fetch-User absent and no Sec-Purpose),
        // so we log out only on the user-activated GET and ignore the speculative ones.
        // no-store so a speculative response can't be reused for the subsequent real click.
        response.setHeader("Cache-Control", "no-store");
        if ("?1".equals(request.getHeader("Sec-Fetch-User"))) {
            SecurityUtils.getSubject().logout();
        }
        response.sendRedirect("/");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityUtils.getSubject().logout();
        response.sendRedirect("/");
    }
}
