<#assign style="mainstyle.css">
<!DOCTYPE html>
<html lang="en" class="shiro-none-active">
<head>
    <#include "inc/_head.ftl">
    <link href="js/lib/jtable/themes/metro/darkgray/jtable.min.css" rel="stylesheet" type="text/css" />

</head>

<body>

<div id="spinner" class="shiro-unset" style="position: absolute; top: 90px; left: 50%;">
</div>

<#include "inc/topbar.ftl">


<div class="container">

<div id="BoatTableContainer"></div>



<#include "inc/footer.ftl">

</div>

<#include "inc/_foot.ftl">
<script src="js/lib/jtable/jquery.jtable.js"></script>

<script type="text/javascript">
$(document).ready(function () {

    // The server stores rowable/event as booleans (the reservation pages rely on that), but
    // jtable's dropdown ("options") matches option keys to the value with ==, and 'true' == true
    // is false in JS -- so booleans never match a string option and the dropdown can't display or
    // preselect. We normalise these two fields to 'true'/'false' strings as records come IN from
    // the server and let them go back OUT as the strings the dropdown posts (which the servlet
    // already parses). This keeps the dropdowns working without changing what other pages receive.
    function boolToStr(record) {
        if (record) {
            record.rowable = record.rowable ? 'true' : 'false';
            record.event = record.event ? 'true' : 'false';
        }
        return record;
    }
    function boatAction(url, postData) {
        return $.Deferred(function ($dfd) {
            $.ajax({ url: url, type: 'POST', dataType: 'json', data: postData })
                .done(function (data) {
                    if (data && data.Records) { $.each(data.Records, function (i, r) { boolToStr(r); }); }
                    if (data && data.Record) { boolToStr(data.Record); }
                    $dfd.resolve(data);
                })
                .fail(function () { $dfd.reject(); });
        });
    }

    $('#BoatTableContainer').jtable({
        title: 'Boats',
        actions: {
            listAction: function (postData) { return boatAction('/boats', postData); },
            createAction: function (postData) { return boatAction('/boats/post', postData); },
            updateAction: function (postData) { return boatAction('/boats/post', postData); },
            deleteAction: '/boats/delete'
        },
        fields: {
        	name: {
        		key: true,
            	create: true,
        		title: 'Boat name',
                width: '30%'
            },
            displayName: {
                create: true,
                title: 'Display name',
                type: 'textarea',
                width: '30%'
            },
        	rowable: {
        		create: true,
            	title: 'Status',
            	options: { 'true': 'rowable', 'false': 'not rowable' },
            	defaultValue: 'true',
                width: '10%'
            },
            event: {
                create: true,
                title: 'Event/Boat',
                options: { 'false': 'boat', 'true': 'event' },
                defaultValue: 'false',
                width: '10%'
            },
        	warningMessage: {
        		create: true,
            	title: 'Warning message',
            	type: 'textarea',
                width: '30%'
            }
        }
    });

    $('#BoatTableContainer').jtable('load');
});

</script>
</body>
</html>
