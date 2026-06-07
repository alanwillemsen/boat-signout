<#assign style="mainstyle.css">
<!DOCTYPE html>
<html lang="en" class="shiro-none-active">
<head>
    <#include "inc/_head.ftl">
    <style type="text/css">
        #usersToolbar { margin: 18px 0; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
        #usersToolbar .spacer { flex: 1 1 auto; }
        #usersToolbar #filter { width: 260px; max-width: 100%; }
        #usersCount { color: #777; }

        #usersTable td, #usersTable th { vertical-align: middle; }
        #usersTable th.sortable { cursor: pointer; white-space: nowrap; -webkit-user-select: none; user-select: none; }
        #usersTable th.sortable:hover { background: #f5f5f5; }
        #usersTable th .arrow { color: #bbb; font-size: 0.8em; }
        #usersTable th.sorted .arrow { color: #333; }
        #usersTable .col-toggle { text-align: center; width: 70px; }
        #usersTable .col-actions { text-align: center; width: 90px; }
        #usersTable .login { color: #999; font-size: 0.85em; word-break: break-all; }
        #usersTable .role-badge { margin-right: 3px; }
        #usersTable tr.suspended td { background: #fcf8e3; }

        #usersPager { margin: 12px 0 40px; display: flex; gap: 10px; align-items: center; }
        #usersPager .spacer { flex: 1 1 auto; }
    </style>
</head>

<body>

<div id="spinner" class="shiro-unset" style="position: absolute; top: 90px; left: 50%;"></div>

<#include "inc/topbar.ftl">

<div class="container">

    <h2 class="shiro-admin">Members</h2>

    <div id="usersMessage" class="text-muted"></div>

    <div class="shiro-admin">
        <div id="usersToolbar">
            <input type="text" id="filter" class="form-control" placeholder="Search name, login or role…">
            <select id="statusFilter" class="form-control" style="width:auto;">
                <option value="all">All members</option>
                <option value="admin">Admins</option>
                <option value="suspended">Suspended</option>
                <option value="active">Active (not suspended)</option>
            </select>
            <span class="spacer"></span>
            <span id="usersCount"></span>
        </div>

        <table id="usersTable" class="table table-striped table-hover">
            <thead>
                <tr>
                    <th class="sortable" data-key="member">Member <span class="arrow"></span></th>
                    <th class="sortable" data-key="lastActive">Last active <span class="arrow"></span></th>
                    <th class="sortable" data-key="registered">Registered <span class="arrow"></span></th>
                    <th>Roles</th>
                    <th class="sortable col-toggle" data-key="admin">Admin <span class="arrow"></span></th>
                    <th class="sortable col-toggle" data-key="suspended">Suspended <span class="arrow"></span></th>
                    <th class="col-actions">Actions</th>
                </tr>
            </thead>
            <tbody></tbody>
        </table>

        <div id="usersPager">
            <button type="button" id="prevPage" class="btn btn-default btn-sm">‹ Prev</button>
            <span id="pageInfo" class="text-muted"></span>
            <button type="button" id="nextPage" class="btn btn-default btn-sm">Next ›</button>
            <span class="spacer"></span>
            <label class="text-muted" style="font-weight:normal;">
                Per page
                <select id="pageSize" class="form-control input-sm" style="width:auto; display:inline-block;">
                    <option>25</option>
                    <option selected>50</option>
                    <option>100</option>
                </select>
            </label>
        </div>
    </div>

    <#include "inc/footer.ftl">

</div>

<#include "inc/_foot.ftl">

<script type="text/javascript">
$(document).ready(function () {
    var base = "${userBaseUrl}";

    // Default sort: most recently active first (matches what admins usually want).
    var state = { search: "", status: "all", sortKey: "lastActive", sortDir: "desc", page: 1, pageSize: 50 };
    var allUsers = [];

    function fmtRegistered(ms) {
        if (ms === null || ms === undefined) { return "—"; }
        return (typeof moment !== "undefined") ? moment(ms).format("ll") : new Date(ms).toLocaleDateString();
    }
    function fmtLastActive(ms) {
        if (ms === null || ms === undefined) { return "never"; }
        return (typeof moment !== "undefined") ? moment(ms).fromNow() : new Date(ms).toLocaleString();
    }
    function notify(msg) {
        if (typeof bootbox !== "undefined") { bootbox.alert(msg); } else { alert(msg); }
    }

    // --- filtering --------------------------------------------------------
    function applyFilters() {
        var q = state.search.trim().toLowerCase();
        return allUsers.filter(function (u) {
            if (state.status === "admin" && !u.admin) { return false; }
            if (state.status === "suspended" && !u.suspended) { return false; }
            if (state.status === "active" && u.suspended) { return false; }
            if (!q) { return true; }
            var hay = ((u.displayName || "") + " " + (u.name || "") + " " + (u.roles || []).join(" ")).toLowerCase();
            return hay.indexOf(q) >= 0;
        });
    }

    // --- sorting ----------------------------------------------------------
    // Empty values (never active / unregistered) always sink to the bottom, regardless of
    // sort direction, so the meaningful rows are never buried under blanks.
    function compare(a, b) {
        var dir = state.sortDir === "asc" ? 1 : -1;
        var av, bv, aEmpty = false, bEmpty = false;
        switch (state.sortKey) {
            case "member":
                av = (a.displayName || a.name || "").toLowerCase();
                bv = (b.displayName || b.name || "").toLowerCase();
                break;
            case "lastActive":
                av = a.lastActive; bv = b.lastActive;
                aEmpty = (av === null || av === undefined); bEmpty = (bv === null || bv === undefined);
                break;
            case "registered":
                av = a.registered; bv = b.registered;
                aEmpty = (av === null || av === undefined); bEmpty = (bv === null || bv === undefined);
                break;
            case "admin": av = a.admin ? 1 : 0; bv = b.admin ? 1 : 0; break;
            case "suspended": av = a.suspended ? 1 : 0; bv = b.suspended ? 1 : 0; break;
            default: return 0;
        }
        if (aEmpty && bEmpty) { return 0; }
        if (aEmpty) { return 1; }
        if (bEmpty) { return -1; }
        if (av < bv) { return -1 * dir; }
        if (av > bv) { return 1 * dir; }
        return 0;
    }

    // --- row actions (update the in-memory model; no re-sort mid-toggle) ---
    function postAction(url, data, onOk, onFail) {
        $.ajax({ type: "POST", url: url, data: data, dataType: "json" })
            .done(function (resp) {
                if (resp && resp.code) { onFail(resp.message || "Action refused"); }
                else { onOk(resp); }
            })
            .fail(function (xhr) { onFail("Request failed (" + xhr.status + ")"); });
    }
    function onToggleAdmin(u, box) {
        var make = box.prop("checked");
        postAction(base + "/role", { username: u.name, admin: make },
            function () { u.admin = make; },
            function (msg) { box.prop("checked", !make); notify(msg); });
    }
    function onToggleSuspend(u, box, tr) {
        var susp = box.prop("checked");
        postAction(base + "/suspend", { username: u.name, suspend: susp },
            function () { u.suspended = susp; tr.toggleClass("suspended", susp); },
            function (msg) { box.prop("checked", !susp); notify(msg); });
    }
    function onDelete(u) {
        var question = "Delete " + u.displayName + "? This permanently removes their account and cannot be undone.";
        var doDelete = function (ok) {
            if (!ok) { return; }
            postAction(base + "/suspend", { username: u.name, "delete": true },
                function () {
                    allUsers = allUsers.filter(function (x) { return x.name !== u.name; });
                    render();
                },
                function (msg) { notify(msg); });
        };
        if (typeof bootbox !== "undefined") { bootbox.confirm(question, doDelete); }
        else { doDelete(window.confirm(question)); }
    }

    // --- rendering --------------------------------------------------------
    function buildRow(u) {
        var tr = $("<tr>");
        if (u.suspended) { tr.addClass("suspended"); }

        var member = $("<td>");
        member.append($("<div>").text(u.displayName || u.name));
        if (u.displayName && u.name && u.displayName !== u.name) {
            member.append($("<div class='login'>").text(u.name));
        }
        tr.append(member);

        tr.append($("<td>").text(fmtLastActive(u.lastActive)));
        tr.append($("<td>").text(fmtRegistered(u.registered)));

        var rolesCell = $("<td>");
        (u.roles || []).forEach(function (r) {
            var cls = (r === "admin") ? "label label-primary role-badge" : "label label-default role-badge";
            rolesCell.append($("<span>").attr("class", cls).text(r));
        });
        tr.append(rolesCell);

        var adminBox = $("<input type='checkbox'>").prop("checked", !!u.admin);
        adminBox.on("change", function () { onToggleAdmin(u, adminBox); });
        tr.append($("<td class='col-toggle'>").append(adminBox));

        var suspBox = $("<input type='checkbox'>").prop("checked", !!u.suspended);
        suspBox.on("change", function () { onToggleSuspend(u, suspBox, tr); });
        tr.append($("<td class='col-toggle'>").append(suspBox));

        var del = $("<button class='btn btn-danger btn-xs'>Delete</button>");
        del.on("click", function () { onDelete(u); });
        tr.append($("<td class='col-actions'>").append(del));

        return tr;
    }

    function updateHeaderArrows() {
        $("#usersTable thead th.sortable").each(function () {
            var th = $(this), active = th.data("key") === state.sortKey;
            th.toggleClass("sorted", active);
            th.find(".arrow").text(active ? (state.sortDir === "asc" ? "▲" : "▼") : "");
        });
    }

    function render() {
        var rows = applyFilters().sort(compare);
        var total = rows.length;
        var pages = Math.max(1, Math.ceil(total / state.pageSize));
        if (state.page > pages) { state.page = pages; }
        if (state.page < 1) { state.page = 1; }
        var start = (state.page - 1) * state.pageSize;
        var pageRows = rows.slice(start, start + state.pageSize);

        var tbody = $("#usersTable tbody").empty();
        pageRows.forEach(function (u) { tbody.append(buildRow(u)); });

        $("#usersCount").text(total + (total === 1 ? " member" : " members")
            + (total !== allUsers.length ? " (of " + allUsers.length + ")" : ""));
        $("#pageInfo").text(total === 0
            ? "No matches"
            : "Showing " + (start + 1) + "–" + (start + pageRows.length) + " · page " + state.page + " of " + pages);
        $("#prevPage").prop("disabled", state.page <= 1);
        $("#nextPage").prop("disabled", state.page >= pages);
        updateHeaderArrows();
    }

    // --- wiring -----------------------------------------------------------
    $("#usersTable thead th.sortable").on("click", function () {
        var key = $(this).data("key");
        if (state.sortKey === key) {
            state.sortDir = (state.sortDir === "asc") ? "desc" : "asc";
        } else {
            state.sortKey = key;
            // Text sorts default A→Z; everything else (dates, flags) defaults high→low.
            state.sortDir = (key === "member") ? "asc" : "desc";
        }
        state.page = 1;
        render();
    });
    $("#filter").on("input", function () { state.search = $(this).val(); state.page = 1; render(); });
    $("#statusFilter").on("change", function () { state.status = $(this).val(); state.page = 1; render(); });
    $("#pageSize").on("change", function () { state.pageSize = parseInt($(this).val(), 10); state.page = 1; render(); });
    $("#prevPage").on("click", function () { state.page--; render(); });
    $("#nextPage").on("click", function () { state.page++; render(); });

    // --- load -------------------------------------------------------------
    $.getJSON(base + "/list")
        .done(function (data) {
            allUsers = (data && data.users) ? data.users : [];
            if (!allUsers.length) { $("#usersMessage").text("No members found."); }
            render();
        })
        .fail(function (xhr) {
            if (xhr.status === 403) { $("#usersMessage").text("You need admin access to view this page."); }
            else { $("#usersMessage").text("Could not load members (" + xhr.status + ")."); }
        });
});
</script>
</body>
</html>
