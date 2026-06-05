
shiro.status = (function(log) {

    var expiry = 300 * 1000;
    
    function sleep(millis, callback) {
        setTimeout(function()
                { callback(); }
        , millis);
    }

    function getStatus() {
        if (typeof(localStorage) != 'undefined') {
            var time = parseInt(localStorage.getItem("shiro.status.time")),
                val = localStorage.getItem("shiro.status.data"),
                now = new Date().getTime();
            if (time) {
                if (time + expiry < now) {
                    localStorage.removeItem("shiro.status.time");
                    localStorage.removeItem("shiro.status.data");
                    return false;
                } else {
                    try {
                        return JSON.parse(val);
                    } catch (e) {
                        return false;
                    }
                }
            } else {
                return false;
            }
        }
        return false;
    }

    function setStatus(val) {
        if (typeof(localStorage) != 'undefined') {
            localStorage.setItem("shiro.status.data", JSON.stringify(val));
            localStorage.setItem("shiro.status.time", new Date().getTime());
        }
    }

    function successDefault() {}

    function errorDefault() {}

    function runStatus(options) {
        var onSuccess = options.success || successDefault(),
            onError = options.error || errorDefault(),
            data = getStatus();

        if (data) {
            log("Saved status");
            onSuccess(data, 'success');
        }  else {
        	var statusUrl = shiro.userBaseUrl+"/status?" + Date.now();
            log("Ajax status: " + statusUrl);
			$.ajax(statusUrl, {
                type: "GET",
                dataType: "json",
                success: function(data, status) {
                    setStatus(data);
                    onSuccess(data, status);
                },
                error: function(xhr) {
                    clearStatus();
                    onError(xhr);
                }
            });
        }
    }

    function clearStatus() {
        if (typeof(localStorage) != 'undefined') {
            localStorage.removeItem("shiro.status.time");
            localStorage.removeItem("shiro.status.data");
        }
    }

    return {
        runStatus : runStatus,
        clearStatus: clearStatus,
        sleep: sleep
    };
})(shiro.log);

$(document).ready(function() {
    prettyPrint();
    shiro.spin.start($("#spinner"));

    shiro.status.runStatus({
        success: function(data, status) {
            shiro.spin.stop();
            if (status == 'success') {
                $("html").removeClass("shiro-none-active");
                if (data.message == "known" && data.authenticated == "true") {
                	$("html").addClass("shiro-user-active");
                    // Prefer the display name (e.g. Discord server nickname) over the raw
                    // principal, which for Discord logins is the internal "discord:<id>".
                    $("span.shiro-principal").text(data.displayName || data.name);
                    if (data.admin == "true") {
                        $("html").addClass("shiro-admin-active");
                    }else{
                    	$("html").addClass("shiro-authenticated-active");
                    }
                    if (typeof(loginRedirect) === "function") { 
                    	loginRedirect();
                    }
                } else {
                    $("html").addClass("shiro-guest-active");
                    console.log("status check failed: " + data.message);
                    shiro.login(shiro.userBaseUrl+"/ajaxLogin", function() {
                    	shiro.status.sleep(500, function(){
                    		location.href = "monthview.ftl";
                    	});
               		 });
                }
            } else {
            }
        },
        error: function(xhr) {
            shiro.spin.stop();
        }
    });
});

$(document).ready(function() {
    $("#settings").click(function(e) {
        if (!$("html").hasClass("shiro-authenticated-active")) {
            e.preventDefault();
            shiro.spin.start($("#spinner"));
            shiro.login(shiro.userBaseUrl+"/ajaxLogin", function() {
                window.location.assign("settings.ftl");
            });
            return false;
        }
    });

    $("#signIn").click(function(e) {
        e.preventDefault();
        shiro.login(shiro.userBaseUrl+"/ajaxLogin", function() {
            window.location.reload();
        });
        return false;
    });

    $("#logout").click(function(e) {
        shiro.status.clearStatus();
        shiro.spin.start($("#spinner"));
    });
});
