<#assign style="mainstyle.css">
<!DOCTYPE html>
<html lang="en" class="shiro-none-active">
<head>
    <#include "inc/_head.ftl">
    <style type="text/css">
        #builder { margin-top: 10px; }
        .panel-num { display:inline-block; width:24px; height:24px; line-height:24px; text-align:center;
            border-radius:50%; background:#337ab7; color:#fff; margin-right:6px; font-size:13px; }
        #boatList { max-height: 420px; overflow-y: auto; border: 1px solid #ddd; border-radius: 3px; padding: 6px 10px; }
        #boatList .boat-row { padding: 2px 0; }
        #boatList label { font-weight: normal; display:block; margin:0; cursor:pointer; }
        #boatList .warn { color:#b06a00; }
        #boatList .not-rowable label { color:#aaa; cursor:not-allowed; }
        #boatCount { margin-top: 6px; color:#555; }
        .day-chip { display:inline-block; background:#eef; border:1px solid #ccd; border-radius:3px;
            padding:2px 8px; margin:3px 4px 0 0; font-size:13px; }
        .day-chip .x { cursor:pointer; color:#a00; margin-left:6px; font-weight:bold; }
        .field-row { margin-bottom: 10px; }
        .field-row label.lbl { display:block; font-weight:bold; margin-bottom:3px; }
        #reviewTable td, #reviewTable th { font-size: 13px; }
        #reviewTable .ok { color:#3c763d; }
        #reviewTable .bad { color:#a94442; }
        #reviewWrap { max-height: 360px; overflow:auto; border:1px solid #ddd; margin-top:8px; }
        #reviewSummary { font-weight:bold; margin-top:10px; }
        .mode-panel { border-left:3px solid #eee; padding-left:12px; margin-top:8px; }
    </style>
</head>

<body>

<div id="spinner" class="shiro-unset" style="position: absolute; top: 90px; left: 50%;"></div>

<#include "inc/topbar.ftl">

<div class="container">

    <div class="page-header" style="margin-top:16px;">
        <a href="monthview.ftl" class="btn btn-default btn-sm pull-right">&larr; Back to calendar</a>
        <h3>Reserve Multiple Boats</h3>
    </div>

    <div id="gridMessage" class="text-muted"></div>

    <div id="builder" class="shiro-user">
        <div class="row">

            <!-- 1. Boats -->
            <div class="col-md-4">
                <h4><span class="panel-num">1</span>Choose boats</h4>
                <input type="text" id="boatSearch" class="form-control" placeholder="Filter boats…">
                <div style="margin:6px 0;">
                    <button type="button" id="selectAllBoats" class="btn btn-default btn-xs">Select all shown</button>
                    <button type="button" id="clearBoats" class="btn btn-default btn-xs">Clear</button>
                    <label style="font-weight:normal; margin-left:8px;">
                        <input type="checkbox" id="showNonRowable"> show non-rowable
                    </label>
                </div>
                <div id="boatList"></div>
                <div id="boatCount">No boats selected</div>
            </div>

            <!-- 2. When -->
            <div class="col-md-8">
                <h4><span class="panel-num">2</span>Choose when</h4>

                <div class="field-row">
                    <label class="radio-inline"><input type="radio" name="mode" value="specific" checked> Specific day(s)</label>
                    <label class="radio-inline"><input type="radio" name="mode" value="block"> Continuous block (regatta)</label>
                </div>

                <!-- Specific days mode -->
                <div id="modeSpecific" class="mode-panel">
                    <div class="field-row">
                        <label class="lbl">Days</label>
                        <input type="date" id="dayInput" class="form-control" style="width:auto; display:inline-block;">
                        <button type="button" id="addDay" class="btn btn-default">Add day</button>
                        <div id="dayChips"></div>
                    </div>
                    <div class="field-row">
                        <label class="lbl">Start time &amp; duration</label>
                        <input type="time" id="startTime" class="form-control" value="06:00" style="width:130px; display:inline-block; vertical-align:middle;">
                        <span style="margin:0 6px;">for</span>
                        <input type="number" id="durationNum" class="form-control" value="90" min="1" style="width:80px; display:inline-block; vertical-align:middle;">
                        <select id="durationUnit" class="form-control" style="width:110px; display:inline-block; vertical-align:middle;">
                            <option>Minutes</option>
                            <option>Hours</option>
                        </select>
                    </div>
                </div>

                <!-- Continuous block mode -->
                <div id="modeBlock" class="mode-panel" style="display:none;">
                    <div class="field-row">
                        <label class="lbl">From</label>
                        <input type="date" id="blockStartDate" class="form-control" style="width:170px; display:inline-block; vertical-align:middle;">
                        <input type="time" id="blockStartTime" class="form-control" value="06:00" style="width:130px; display:inline-block; vertical-align:middle;">
                    </div>
                    <div class="field-row">
                        <label class="lbl">To</label>
                        <input type="date" id="blockEndDate" class="form-control" style="width:170px; display:inline-block; vertical-align:middle;">
                        <input type="time" id="blockEndTime" class="form-control" value="18:00" style="width:130px; display:inline-block; vertical-align:middle;">
                    </div>
                    <p class="text-muted">Each selected boat gets one reservation spanning this whole period.</p>
                </div>

                <div class="field-row" style="margin-top:14px;">
                    <button type="button" id="previewBtn" class="btn btn-primary">Preview &amp; check availability</button>
                </div>
            </div>
        </div>

        <!-- 3. Review -->
        <div id="review" style="display:none;">
            <h4><span class="panel-num">3</span>Review</h4>
            <div id="reviewSummary"></div>
            <div id="reviewWrap">
                <table id="reviewTable" class="table table-condensed table-striped">
                    <thead><tr><th>Boat</th><th>Day</th><th>Time</th><th>Status</th></tr></thead>
                    <tbody></tbody>
                </table>
            </div>
            <div style="margin:12px 0 40px;">
                <button type="button" id="signOutAll" class="btn btn-success" disabled>Sign out all</button>
                <span id="signOutNote" class="text-muted" style="margin-left:10px;"></span>
            </div>
        </div>
    </div>

    <#include "inc/footer.ftl">

</div>

<#include "inc/_foot.ftl">
<script type="text/javascript" src="js/reservemulti.js?v=20260607-2"></script>
</body>
</html>
