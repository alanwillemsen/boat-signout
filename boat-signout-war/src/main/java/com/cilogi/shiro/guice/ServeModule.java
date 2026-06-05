// Copyright (c) 2010 Tim Niblett All Rights Reserved.
//
// File:        ServeModule.java  (05-Oct-2010)
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


package com.cilogi.shiro.guice;

import java.util.Map;
import java.util.logging.Logger;

import com.cilogi.shiro.web.SessionCleanupServlet;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.crc.boat.reservation.web.BoatServlet;
import org.crc.boat.reservation.web.ProfilePictureServlet;
import org.crc.boat.reservation.web.ProfileServlet;
import org.crc.boat.reservation.web.ReservationServlet;
import org.crc.boat.reservation.web.SystemSettingsServlet;

import com.cilogi.shiro.web.FreemarkerServlet;
import com.cilogi.shiro.web.MailQueueServlet;
import com.cilogi.shiro.web.MailReceiveServlet;
import com.cilogi.shiro.web.WakeServlet;
import com.cilogi.shiro.web.user.ConfirmServlet;
import com.cilogi.shiro.web.user.LoginServlet;
import com.cilogi.shiro.web.user.RegisterServlet;
import com.cilogi.shiro.web.user.SettingsServlet;
import com.cilogi.shiro.web.user.StatusServlet;
import com.cilogi.shiro.web.user.UserListServlet;
import com.cilogi.shiro.web.user.UserSuspendServlet;
import com.cilogi.shiro.web.discord.DiscordAuthServlet;
//import com.google.appengine.tools.appstats.AppstatsFilter;
//import com.google.appengine.tools.appstats.AppstatsServlet;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.servlet.ServletModule;
import com.googlecode.objectify.cache.AsyncCacheFilter;


public class ServeModule extends ServletModule {
    static final Logger LOG = Logger.getLogger(ServeModule.class.getName());

    private final String userBaseUrl;
    
    public ServeModule(String userBaseUrl) {
        Preconditions.checkArgument(userBaseUrl != null && !userBaseUrl.endsWith("/"));
        this.userBaseUrl = userBaseUrl;
    }

    @Override
    protected void configureServlets() {
        LOG.warning(userBaseUrl);
        filter("/*").through(ShiroFilter.class);
        filter("/*").through(AsyncCacheFilter.class);
//        filter("/*").through(AppstatsFilter.class, map("calculateRpcCosts", "true"));

        serve("*.ftl").with(FreemarkerServlet.class);

        serve(userBaseUrl + "/ajaxLogin").with(LoginServlet.class);
        serve(userBaseUrl + "/register").with(RegisterServlet.class);
        serve(userBaseUrl + "/registermail").with(MailQueueServlet.class);
        serve(userBaseUrl + "/confirm").with(ConfirmServlet.class);
        serve(userBaseUrl + "/status").with(StatusServlet.class);
        serve(userBaseUrl + "/list").with(UserListServlet.class);
        serve(userBaseUrl + "/suspend").with(UserSuspendServlet.class);
        serve(userBaseUrl + "/settings*").with(SettingsServlet.class);
        serve("/picture*").with(ProfilePictureServlet.class);
        serve("/profile*").with(ProfileServlet.class);
        serve("/systemSettings*").with(SystemSettingsServlet.class);
            // this one is here so that the default login filter works
        serve("/login").with(LoginServlet.class);
            // Login with Discord (OAuth2)
        serve("/discord/login").with(DiscordAuthServlet.class);
        serve("/discord/callback").with(DiscordAuthServlet.class);
            // Lets check mail to see when stuff bounces
        serve("/_ah/mail/*").with(MailReceiveServlet.class);
//        serve("/appstats/*").with(AppstatsServlet.class);
        serve("/cron/wake").with(WakeServlet.class);
        serve("/cron/sessioncleanup").with(SessionCleanupServlet.class);

        serve("/boats*").with(BoatServlet.class);
        serve("/reserve*").with(ReservationServlet.class);

        serve("/whoami*").with(StatusServlet.class);
    }

    private static Map<String,String> map(String... params) {
        Preconditions.checkArgument(params.length % 2 == 0, "You have to have a n even number of map params");
        Map<String,String> map = Maps.newHashMap();
        for (int i = 0; i < params.length; i+=2) {
            map.put(params[i], params[i+1]);
        }
        return map;
    }
}
