// Copyright (c) 2011 Tim Niblett All Rights Reserved.
//
// File:        BaseServlet.java  (31-Oct-2011)
// Author:      tim

//
// Copyright in the whole and every part of this source file belongs to
// Tim Niblett (the Author) and may not be used,
// sold, licenced, transferred, copied or reproduced in whole or in
// part in any manner or form or in or on any media to any person
// other than in accordance with the terms of The Author's agreement
// or otherwise without the prior written consent of The Author.  All
// information contained in this source file is confidential information
// belonging to The Author and as such may not be disclosed other
// than in accordance with the terms of The Author's agreement, or
// otherwise, without the prior written consent of The Author.  As
// confidential information this source file must be kept fully and
// effectively secure at all times.
//


package com.cilogi.shiro.web;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.crc.boat.reservation.dao.SettingsDao;
import org.json.JSONException;
import org.json.JSONObject;

import com.cilogi.shiro.gae.GaeUser;
import com.cilogi.shiro.gae.GaeUserDAO;
import com.cilogi.util.MimeTypes;
import com.cilogi.util.doc.CreateDoc;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;


public class BaseServlet extends HttpServlet implements ParameterNames, MimeTypes {
    static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    protected final String MESSAGE = "message";
    protected final String CODE = "code";

    protected final int HTTP_STATUS_OK = 200;
    protected final int HTTP_STATUS_NOT_FOUND = 404;
    protected final int HTTP_STATUS_FORBIDDEN = 403;
    protected final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;

    /** Don't write lastActive on every single request -- only refresh it once the recorded
     *  value is older than this. Keeps the per-request datastore write down to roughly one
     *  per active user per 5 minutes while still giving admins an accurate "last seen". */
    private static final long LAST_ACTIVE_THROTTLE_MS = 5 * 60 * 1000L;

    private CreateDoc create;

    protected Provider<GaeUserDAO> daoProvider;
    protected SettingsDao settingsDao = new SettingsDao();

    protected BaseServlet(Provider<GaeUserDAO> daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    protected void setCreate(CreateDoc create) {
        this.create = create;
    }

    protected void issue(String mimeType, int returnCode, String output, HttpServletResponse response) throws IOException {
        response.setContentType(mimeType);
        response.setStatus(returnCode);
        response.getWriter().println(output);
    }

    protected void issueJson(HttpServletResponse response, int status, String... args) throws IOException {
        Preconditions.checkArgument(args.length % 2 == 0, "There must be an even number of strings");
            try {
            JSONObject obj = new JSONObject();
            for (int i = 0; i < args.length; i += 2) {
                obj.put(args[i], args[i+1]);
            }
            issueJson(response, status, obj);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    protected void issueJson(HttpServletResponse response, int status, JSONObject obj) throws IOException {
        issue(MIME_APPLICATION_JSON, status, obj.toString(), response);
    }

    protected void showView(HttpServletResponse response, String templateName, Object... args) throws IOException {
        showView(response, templateName, CreateDoc.map(args));
    }

    protected void showView(HttpServletResponse response, String templateName, Map<String,Object> args) throws IOException {
         String html =  create.createDocumentString(templateName, args);
         issue(MIME_TEXT_HTML, HTTP_STATUS_OK, html, response);
     }

    protected String view(String templateName, Object... args) {
        return create.createDocumentString(templateName, CreateDoc.map(args));
    }

    protected int intParameter(String name, HttpServletRequest request, int deflt) {
        String s = request.getParameter(name);
        return (s == null) ? deflt : Integer.parseInt(s);
    }

    protected boolean booleanParameter(String name, HttpServletRequest request, boolean deflt) {
        String s = request.getParameter(name);
        return (s == null) ? deflt : Boolean.parseBoolean(s);
    }

    /**
     * True if this looks like a speculative (prefetch/prerender) request rather than a real
     * user action. Browsers speculatively fetch links, so any endpoint that changes server
     * state on GET (login, logout) must ignore these or users get logged out mid-session.
     */
    protected static boolean isSpeculativeRequest(HttpServletRequest request) {
        String secPurpose = request.getHeader("Sec-Purpose"); // Chrome: "prefetch", "prefetch;prerender"
        if (secPurpose != null && (secPurpose.contains("prefetch") || secPurpose.contains("prerender"))) {
            return true;
        }
        String purpose = request.getHeader("Purpose"); // older browsers
        if ("prefetch".equalsIgnoreCase(purpose)) {
            return true;
        }
        String xPurpose = request.getHeader("X-Purpose");
        if (xPurpose != null && xPurpose.toLowerCase().contains("prefetch")) {
            return true;
        }
        String xMoz = request.getHeader("X-moz"); // Firefox
        return xMoz != null && xMoz.toLowerCase().contains("prefetch");
    }

    /**
     * Login and make sure you then have a new session.  This helps prevent session fixation attacks.
     *
     * @param token
     * @param subject
     */
    protected static void loginWithNewSession(AuthenticationToken token, Subject subject) {
        Session originalSession = subject.getSession();

        Map<Object, Object> attributes = Maps.newLinkedHashMap();
        Collection<Object> keys = originalSession.getAttributeKeys();
        for(Object key : keys) {
            Object value = originalSession.getAttribute(key);
            if (value != null) {
                attributes.put(key, value);
            }
        }
        originalSession.stop();
        subject.login(token);

        Session newSession = subject.getSession();
        for(Object key : attributes.keySet() ) {
            newSession.setAttribute(key, attributes.get(key));
        }
    }

    protected boolean isCurrentUserAdmin() {
        Subject subject = SecurityUtils.getSubject();
        return subject.hasRole("admin");
    }

    protected GaeUser getCurrentGaeUser() {
        Subject subject = SecurityUtils.getSubject();
        String email = (String)subject.getPrincipal();
        if (email == null) {
            return null;
        } else {
            GaeUserDAO dao = daoProvider.get();
            GaeUser user = dao.findUser(email);
            // Treat a suspended user as "not logged in" so revoking access takes effect on
            // the user's very next request, even if their session is still valid (we now
            // keep sessions alive for 90 days). Callers already redirect a null user to
            // /logout, which clears the session. Without this, the realm only blocks
            // suspended users at login/role-checks, so a suspended member could keep
            // reserving boats until their long-lived session finally expired.
            if (user != null && user.isSuspended()) {
                return null;
            }
            if (user != null) {
                touchLastActive(user, dao);
            }
            return user;
        }
    }

    /** Record that the user was just active, but at most once per LAST_ACTIVE_THROTTLE_MS to
     *  avoid a datastore write on every request. Best-effort: a failure here must never break
     *  the request, so we just log it. */
    private void touchLastActive(GaeUser user, GaeUserDAO dao) {
        Date last = user.getLastActive();
        long now = System.currentTimeMillis();
        if (last == null || now - last.getTime() > LAST_ACTIVE_THROTTLE_MS) {
            try {
                user.setLastActive(new Date(now));
                dao.saveUser(user, false);
            } catch (Exception e) {
                LOG.warning("Could not update lastActive for " + user.getName() + ": " + e.getMessage());
            }
        }
    }
    
}
