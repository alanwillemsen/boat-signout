
<script
        src="https://code.jquery.com/jquery-3.7.1.min.js"
        integrity="sha256-/JqT3SQfawRcv/BIHPThkBvs0OEvtFFmqPF/lYI/Cxo="
        crossorigin="anonymous"></script>
<script>
	$.ajaxSetup({ cache: false });
</script>
<script src="https://code.jquery.com/ui/1.13.2/jquery-ui.min.js" integrity="sha256-lSjKY0/srUM9BE3dPm+c4fBo1dky2v27Gdjm2uoZaL0=" crossorigin="anonymous"></script>

<script src="${staticBaseUrl}js/lib/jquery.tmpl.min.js"></script>

<script src="${staticBaseUrl}js/lib/jquery.validate.min.js"></script>
<script src="${staticBaseUrl}js/lib/spin.min.js"></script>
<script src="${staticBaseUrl}js/lib/jquery.busy.min.js"></script>

<script src="${staticBaseUrl}assets/js/google-code-prettify/prettify.js"></script>

<script src="${staticBaseUrl}js/lib/underscore/underscore-min.js"></script>
<script src="${staticBaseUrl}js/lib/bootstrap3/js/bootstrap.min.js"></script>
<script src="${staticBaseUrl}js/lib/jstimezonedetect/jstz.min.js"></script>
<script src="${staticBaseUrl}js/lib/moment.js"></script>
<script src="${staticBaseUrl}js/lib/datetime/js/bootstrap-datetimepicker.js"></script>
<script src="${staticBaseUrl}js/lib/validator/js/bootstrapValidator.js"></script>
<script src="${staticBaseUrl}js/lib/bootbox.min.js"></script>
<script src="${staticBaseUrl}js/lib/select2.js"></script>
<script  src="${staticBaseUrl}js/lib/calendar.js"></script>
<script  src="${staticBaseUrl}js/lib/jquery.cookie.js"></script>

<script src="${staticBaseUrl}js/init.js"></script>
<script src="${staticBaseUrl}js/spin.js"></script>
<script src="${staticBaseUrl}js/log.js"></script>
<script src="${staticBaseUrl}js/status.js?v=20260605"></script>
<script src="${staticBaseUrl}js/login.js"></script>

<script>
	$(document).ready(function() {
		$('#logout').click( function() { 
			shiro.status.clearStatus(); 
			location.href = "/"; 
		} );
	});
</script>
