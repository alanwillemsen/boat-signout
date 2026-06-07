// Copyright (c) 2011 Tim Niblett All Rights Reserved.
//
// File:        UserListServlet.java  (11-Nov-2011)
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


package com.cilogi.shiro.web.user;

import com.cilogi.shiro.gae.GaeUser;
import com.cilogi.shiro.gae.GaeUserDAO;
import com.cilogi.shiro.web.BaseServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Returns the full member list as JSON for the admin members page (users.ftl). Admin only.
 * The list is small (a single rowing club), so we load everything and let the page filter
 * client-side rather than paginate. Dates are emitted as epoch millis (or null) and
 * formatted in the browser.
 */
@Singleton
public class UserListServlet extends BaseServlet {
    static final Logger LOG = Logger.getLogger(UserListServlet.class.getName());

    @Inject
    UserListServlet(Provider<GaeUserDAO> daoProvider) {
        super(daoProvider);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isCurrentUserAdmin()) {
            issueJson(response, HTTP_STATUS_FORBIDDEN, MESSAGE, "Only admins can list members");
            return;
        }
        try {
            List<GaeUser> users = new ArrayList<>(daoProvider.get().listAllUsers());
            // Most recently active first; users who have never been seen sink to the bottom.
            users.sort(Comparator.comparing(
                    UserListServlet::lastActiveMillis, Comparator.reverseOrder()));

            JSONArray array = new JSONArray();
            for (GaeUser user : users) {
                array.put(toJson(user));
            }
            JSONObject out = new JSONObject();
            out.put("users", array);
            issueJson(response, HTTP_STATUS_OK, out);
        } catch (JSONException e) {
            LOG.severe("Error building user list: " + e.getMessage());
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_INTERNAL_SERVER_ERROR,
                    "Error generating JSON: " + e.getMessage(), response);
        }
    }

    private static long lastActiveMillis(GaeUser user) {
        Date d = user.getLastActive();
        return d == null ? Long.MIN_VALUE : d.getTime();
    }

    private static JSONObject toJson(GaeUser user) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", user.getName());
        o.put("displayName", user.getDisplayName() == null ? user.getName() : user.getDisplayName());
        o.put("registered", millisOrNull(user.getDateRegistered()));
        o.put("lastActive", millisOrNull(user.getLastActive()));
        o.put("roles", new JSONArray(user.getRoles()));
        o.put("admin", user.isAdmin());
        o.put("suspended", user.isSuspended());
        o.put("discord", user.getDiscordId() != null);
        return o;
    }

    private static Object millisOrNull(Date date) {
        return date == null ? JSONObject.NULL : date.getTime();
    }
}
