// Shiro realm for users who logged in via Discord.
//
// Mirrors DatastoreRealm but for DiscordAuthenticationToken: the Discord OAuth
// flow (and guild-membership check) has already happened in DiscordAuthServlet,
// so authentication here is just "does this Discord-backed GaeUser exist and is
// it qualified".  Credentials always match (AllowAllCredentialsMatcher) because
// there is no password for Discord users.

package com.cilogi.shiro.gae;

import java.util.logging.Logger;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import com.google.common.base.Preconditions;


public class DiscordRealm extends AuthorizingRealm {
    private static final Logger LOG = Logger.getLogger(DiscordRealm.class.getName());

    public DiscordRealm() {
        super(new AllowAllCredentialsMatcher());
        // Only this realm handles Discord tokens; UsernamePasswordToken logins
        // continue to be handled by iniRealm / DatastoreRealm.
        setAuthenticationTokenClass(DiscordAuthenticationToken.class);
    }

    private GaeUserDAO dao() {
        return new GaeUserDAO();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String principal = (String) token.getPrincipal();
        Preconditions.checkNotNull(principal, "Discord principal can't be null");

        GaeUser user = dao().findUser(principal);
        if (user == null || userIsNotQualified(user)) {
            LOG.info("Rejecting Discord principal " + principal);
            return null;
        }
        return new SimpleAuthenticationInfo(user.getName(), token.getCredentials(), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        Preconditions.checkNotNull(principals, "You can't have a null collection of principals");
        String principal = (String) getAvailablePrincipal(principals);
        if (principal == null) {
            return null;
        }
        GaeUser user = dao().findUser(principal);
        if (user == null || userIsNotQualified(user)) {
            return null;
        }
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo(user.getRoles());
        info.setStringPermissions(user.getPermissions());
        return info;
    }

    private static boolean userIsNotQualified(GaeUser user) {
        return !user.isRegistered() || user.isSuspended();
    }
}
