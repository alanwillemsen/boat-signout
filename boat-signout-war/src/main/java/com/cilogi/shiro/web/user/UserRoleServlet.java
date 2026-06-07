// Admin-only endpoint for granting/revoking the "admin" role on another user.
//
// POST /user/admin/role with:
//   username = <the member's login/principal>
//   admin    = true|false   (true -> grant admin, false -> revoke admin)
//
// Only an existing admin may call this (checked server-side; the realm gives the "admin"
// role full permissions, see shiro.ini). An admin cannot revoke their OWN admin access, so
// there is always at least the acting admin left and nobody can accidentally lock the whole
// club out of the admin tools.

package com.cilogi.shiro.web.user;

import com.cilogi.shiro.gae.GaeUser;
import com.cilogi.shiro.gae.GaeUserDAO;
import com.cilogi.shiro.web.BaseServlet;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

@Singleton
public class UserRoleServlet extends BaseServlet {
    static final Logger LOG = Logger.getLogger(UserRoleServlet.class.getName());

    @Inject
    UserRoleServlet(Provider<GaeUserDAO> daoProvider) {
        super(daoProvider);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isCurrentUserAdmin()) {
            issueJson(response, HTTP_STATUS_FORBIDDEN, MESSAGE, "Only admins can change roles");
            return;
        }
        try {
            String userName = request.getParameter(USERNAME);
            boolean makeAdmin = Boolean.parseBoolean(request.getParameter(ADMIN));
            GaeUserDAO dao = daoProvider.get();
            GaeUser user = dao.findUser(userName);
            if (user == null) {
                issue(MIME_TEXT_PLAIN, HTTP_STATUS_NOT_FOUND, "Can't find user " + userName, response);
                return;
            }

            GaeUser current = getCurrentGaeUser();
            if (!makeAdmin && current != null && current.getName().equalsIgnoreCase(user.getName())) {
                issueJson(response, HTTP_STATUS_OK,
                        MESSAGE, "You can't remove your own admin access", CODE, "400");
                return;
            }

            if (makeAdmin) {
                user.getRoles().add(GaeUser.ROLE_ADMIN);
            } else {
                user.getRoles().remove(GaeUser.ROLE_ADMIN);
            }
            dao.saveUser(user, false);
            issueJson(response, HTTP_STATUS_OK,
                    MESSAGE, makeAdmin
                            ? "User " + userName + " is now an admin"
                            : "User " + userName + " is no longer an admin");
        } catch (Exception e) {
            LOG.severe("Role change failure: " + e.getMessage());
            issue(MIME_TEXT_PLAIN, HTTP_STATUS_INTERNAL_SERVER_ERROR,
                    "Error changing role: " + e.getMessage(), response);
        }
    }
}
