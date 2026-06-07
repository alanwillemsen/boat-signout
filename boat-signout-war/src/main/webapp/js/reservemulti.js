// "Reserve Multiple" schedule builder: pick a set of boats, then either specific day(s) at a
// time, or one continuous block (regatta), preview every reservation (with conflict check), and
// create them all in one POST /reserve/batch. Mirrors the app convention: browser-local moment()
// for display, server parses in America/Toronto.
(function ($) {
    "use strict";

    var RIVER_BOAT = "River Not Safe For Rowing";

    function getUrlVar(key) {
        var r = new RegExp(key + "=([^&]*)", "i").exec(window.location.search);
        return (r && decodeURIComponent(r[1])) || "";
    }
    function escapeHtml(s) { return $("<div>").text(s == null ? "" : s).html(); }
    function escapeAttr(s) { return (s == null ? "" : String(s)).replace(/"/g, "&quot;"); }
    function overlaps(aStart, aEnd, bStart, bEnd) { return aStart < bEnd && aEnd > bStart; }

    var boats = [];               // [{name,displayName,rowable,warningMessage,event}]
    var selectedBoats = {};       // name -> true
    var dates = [];               // ["YYYY-MM-DD"] for specific-days mode
    var currentPlan = [];         // last previewed plan items

    function boatDisplay(name) {
        for (var i = 0; i < boats.length; i++) { if (boats[i].name === name) { return boats[i].displayName || name; } }
        return name;
    }

    // ---- Boats panel ----
    function renderBoatList() {
        var q = ($("#boatSearch").val() || "").toLowerCase();
        var showNon = $("#showNonRowable").is(":checked");
        var html = "";
        boats.forEach(function (b) {
            if (!b.rowable && !showNon) { return; }
            var hay = ((b.displayName || "") + " " + (b.name || "")).toLowerCase();
            if (q && hay.indexOf(q) < 0) { return; }
            var checked = selectedBoats[b.name] ? " checked" : "";
            var cls = b.rowable ? "boat-row" : "boat-row not-rowable";
            var dis = b.rowable ? "" : " disabled";
            var warn = (!b.rowable || b.warningMessage)
                ? ' <span class="warn" title="' + escapeAttr((!b.rowable ? "Not rowable. " : "") + (b.warningMessage || "")) + '">&#9888;</span>'
                : "";
            html += '<div class="' + cls + '"><label><input type="checkbox" class="boatcb" data-name="'
                + escapeAttr(b.name) + '"' + checked + dis + '> ' + escapeHtml(b.displayName || b.name) + warn + "</label></div>";
        });
        $("#boatList").html(html || '<div class="text-muted">No boats match.</div>');
    }

    function updateBoatCount() {
        var n = Object.keys(selectedBoats).length;
        $("#boatCount").text(n ? (n + " boat" + (n > 1 ? "s" : "") + " selected") : "No boats selected");
    }

    // ---- Days panel ----
    function renderChips() {
        var html = "";
        dates.forEach(function (d) {
            html += '<span class="day-chip" data-d="' + d + '">' + moment(d, "YYYY-MM-DD").format("ddd MMM D")
                + ' <span class="x" data-d="' + d + '">&times;</span></span>';
        });
        $("#dayChips").html(html);
    }

    // ---- Build the plan ----
    function buildPlan() {
        var names = Object.keys(selectedBoats);
        if (!names.length) { return { error: "Select at least one boat." }; }
        var mode = $("input[name=mode]:checked").val();
        var items = [];

        if (mode === "specific") {
            if (!dates.length) { return { error: "Add at least one day." }; }
            var t = $("#startTime").val();
            if (!t) { return { error: "Set a start time." }; }
            var rt = moment(t, "HH:mm").format("h:mm A");
            var num = parseInt($("#durationNum").val(), 10);
            if (!num || num < 1) { return { error: "Set a valid duration." }; }
            var unit = $("#durationUnit").val();
            var mins = (unit === "Hours") ? num * 60 : num;
            dates.forEach(function (d) {
                var start = moment(d + " " + t, "YYYY-MM-DD HH:mm");
                var startMs = start.valueOf();
                var endMs = start.clone().add(mins, "minutes").valueOf();
                names.forEach(function (n) {
                    items.push({ boat: n, boatDisplay: boatDisplay(n), reserveDate: d, reserveTime: rt,
                        duration: String(num), units: unit, startMs: startMs, endMs: endMs });
                });
            });
        } else {
            var sd = $("#blockStartDate").val(), st = $("#blockStartTime").val();
            var ed = $("#blockEndDate").val(), et = $("#blockEndTime").val();
            if (!sd || !st || !ed || !et) { return { error: "Set the block start and end." }; }
            var bStart = moment(sd + " " + st, "YYYY-MM-DD HH:mm");
            var bEnd = moment(ed + " " + et, "YYYY-MM-DD HH:mm");
            if (!bEnd.isAfter(bStart)) { return { error: "End must be after start." }; }
            var totalMin = Math.round((bEnd.valueOf() - bStart.valueOf()) / 60000);
            var brt = bStart.format("h:mm A");
            names.forEach(function (n) {
                items.push({ boat: n, boatDisplay: boatDisplay(n), reserveDate: sd, reserveTime: brt,
                    duration: String(totalMin), units: "Minutes", startMs: bStart.valueOf(), endMs: bEnd.valueOf() });
            });
        }
        return { items: items };
    }

    // ---- Preview (fetch existing reservations, flag conflicts) ----
    function preview() {
        var plan = buildPlan();
        if (plan.error) { bootbox.alert(plan.error); return; }
        var items = plan.items;
        var minStart = items[0].startMs, maxEnd = items[0].endMs;
        items.forEach(function (i) { if (i.startMs < minStart) { minStart = i.startMs; } if (i.endMs > maxEnd) { maxEnd = i.endMs; } });

        var spin = shiro.spin.start($("#spinner"));
        $.getJSON("reserve?from=" + minStart + "&to=" + maxEnd).done(function (res) {
            spin.stop();
            var byBoat = {}, river = [];
            ((res && res.result) || []).forEach(function (r) {
                if (r.boatName === RIVER_BOAT) { river.push({ start: r.start, end: r.end }); return; }
                (byBoat[r.boatName] = byBoat[r.boatName] || []).push({ start: r.start, end: r.end, who: r.userDisplayName });
            });

            var okCount = 0, badCount = 0, rows = "";
            items.forEach(function (it) {
                var status = "OK", cls = "ok", k;
                for (k = 0; k < river.length; k++) {
                    if (overlaps(it.startMs, it.endMs, river[k].start, river[k].end)) { status = "River closed"; cls = "bad"; break; }
                }
                if (cls === "ok") {
                    var list = byBoat[it.boat] || [];
                    for (k = 0; k < list.length; k++) {
                        if (overlaps(it.startMs, it.endMs, list[k].start, list[k].end)) { status = "Taken by " + list[k].who; cls = "bad"; break; }
                    }
                }
                it._ok = (cls === "ok");
                if (it._ok) { okCount++; } else { badCount++; }
                var sameDay = moment(it.startMs).isSame(moment(it.endMs), "day");
                var timeStr = moment(it.startMs).format("h:mm A") + " – " + moment(it.endMs).format("h:mm A")
                    + (sameDay ? "" : " (" + moment(it.endMs).format("ddd MMM D") + ")");
                rows += "<tr><td>" + escapeHtml(it.boatDisplay) + "</td><td>" + moment(it.startMs).format("ddd MMM D")
                    + "</td><td>" + timeStr + "</td><td class='" + cls + "'>" + escapeHtml(status) + "</td></tr>";
            });
            $("#reviewTable tbody").html(rows);
            $("#reviewSummary").html("Will create <b>" + items.length + "</b> reservation(s): "
                + "<span class='text-success'>" + okCount + " available</span>"
                + (badCount ? ", <span class='text-danger'>" + badCount + " unavailable (skipped)</span>" : ""));
            currentPlan = items;
            $("#signOutAll").prop("disabled", okCount === 0);
            $("#signOutNote").text(badCount ? (badCount + " unavailable will be skipped.") : "");
            $("#review").show();
            $("html,body").animate({ scrollTop: $("#review").offset().top - 10 }, 200);
        }).fail(function (xhr) {
            spin.stop();
            if (xhr && xhr.status === 403) { location.href = "/"; }
            else { bootbox.alert("Could not check availability (" + (xhr && xhr.status) + ")."); }
        });
    }

    // ---- Submit (only the available items; server re-checks for races) ----
    function signOutAll() {
        var ok = currentPlan.filter(function (i) { return i._ok; });
        if (!ok.length) { return; }
        var batch = ok.map(function (i) {
            return { boat: i.boat, reserveDate: i.reserveDate, reserveTime: i.reserveTime, duration: i.duration, units: i.units };
        });
        var spin = shiro.spin.start($("#spinner"));
        $.ajax({
            type: "POST", url: "reserve/batch", contentType: "application/json", dataType: "json",
            data: JSON.stringify({ allowConflicts: false, items: batch }),
            success: function (resp) {
                spin.stop();
                if (resp && resp.Result === "ERROR") { bootbox.alert(resp.Message || "Could not reserve."); return; }
                var created = resp.created || 0;
                var msg = "Signed out " + created + " reservation" + (created === 1 ? "" : "s") + ".";
                if (resp.failed && resp.failed.length) {
                    var lines = resp.failed.slice(0, 30).map(function (f) {
                        return "&bull; " + escapeHtml(f.boat) + " " + escapeHtml(f.reserveTime) + " &mdash; " + (f.message || f.errorType);
                    });
                    msg += "<br><br><b>" + resp.failed.length + " skipped:</b><br>" + lines.join("<br>");
                }
                bootbox.alert(msg, function () { preview(); });
            },
            error: function (xhr) {
                spin.stop();
                if (xhr.status === 403) { location.href = "/"; }
                else { bootbox.alert("Request failed (" + xhr.status + ")."); }
            }
        });
    }

    $(document).ready(function () {
        var dt = getUrlVar("dt") || moment().format("YYYY-MM-DD");
        $("#dayInput").val(dt);
        $("#blockStartDate").val(dt);
        $("#blockEndDate").val(dt);

        // Boats
        $("#boatSearch").on("input", renderBoatList);
        $("#showNonRowable").on("change", renderBoatList);
        $("#boatList").on("change", ".boatcb", function () {
            var n = $(this).data("name");
            if ($(this).is(":checked")) { selectedBoats[n] = true; } else { delete selectedBoats[n]; }
            updateBoatCount();
        });
        $("#selectAllBoats").click(function () {
            $("#boatList .boatcb:not(:disabled)").each(function () { selectedBoats[$(this).data("name")] = true; });
            renderBoatList(); updateBoatCount();
        });
        $("#clearBoats").click(function () { selectedBoats = {}; renderBoatList(); updateBoatCount(); });

        // Mode toggle
        $("input[name=mode]").change(function () {
            if ($(this).val() === "specific") { $("#modeSpecific").show(); $("#modeBlock").hide(); }
            else { $("#modeBlock").show(); $("#modeSpecific").hide(); }
        });

        // Days
        $("#addDay").click(function () {
            var v = $("#dayInput").val();
            if (v && dates.indexOf(v) < 0) {
                dates.push(v);
                dates.sort();
                renderChips();
            }
        });
        $("#dayChips").on("click", ".x", function () {
            var d = $(this).data("d");
            dates = dates.filter(function (x) { return x !== d; });
            renderChips();
        });

        $("#previewBtn").click(preview);
        $("#signOutAll").click(signOutAll);

        // Load boats
        var spin = shiro.spin.start($("#spinner"));
        $.getJSON("boats").done(function (data) {
            spin.stop();
            boats = ((data && data.Records) || []).slice().sort(function (a, b) {
                if (!!a.rowable !== !!b.rowable) { return a.rowable ? -1 : 1; }
                return (a.displayName || a.name || "").toLowerCase().localeCompare((b.displayName || b.name || "").toLowerCase());
            });
            renderBoatList();
        }).fail(function (xhr) {
            spin.stop();
            if (xhr && xhr.status === 403) { $("#gridMessage").text("Please log in to reserve."); }
            else { $("#gridMessage").text("Could not load boats (" + (xhr && xhr.status) + ")."); }
        });
    });

}(jQuery));
