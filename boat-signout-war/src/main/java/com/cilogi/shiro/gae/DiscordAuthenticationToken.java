// Discord OAuth authentication token.
//
// Carries the already-validated Discord principal (the synthetic GaeUser name
// "discord:<id>").  By the time this token is created the Discord OAuth flow has
// completed and guild membership has been verified, so there is no secret to
// check -- DiscordRealm uses an AllowAllCredentialsMatcher.

package com.cilogi.shiro.gae;

import org.apache.shiro.authc.AuthenticationToken;

public class DiscordAuthenticationToken implements AuthenticationToken {

    private final String principal;

    public DiscordAuthenticationToken(String principal) {
        this.principal = principal;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return principal;
    }
}
