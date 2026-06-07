function pad(s) { return (s < 10) ? '0' + s : s; }
(function($) {

	"use strict";

	var options = {
		events_source: 'reserve',
		view: 'month',
		tmpl_path: 'tmpls/',
		tmpl_cache: false,
		//day: '2013-03-12',
		onAfterViewLoad: function(view) {
			$('.page-header h3').text(this.getTitle());
			$('.btn-group button').removeClass('active');
			$('button[data-calendar-view="' + view + '"]').addClass('active');
			
		},
		classes: {
			months: {
				general: 'label'
			}
		}
	};

	var calendar = $('#calendar').calendar(options);

	$('.btn-group button[data-calendar-nav]').each(function() {
		var $this = $(this);
		$this.click(function() {
			calendar.navigate($this.data('calendar-nav'));
		});
	});

	$('.btn-group button[data-calendar-view]').each(function() {
		var $this = $(this);
		$this.click(function() {
			calendar.view($this.data('calendar-view'));
		});
	});
	

	var options = {
			dragLockToAxis: true,
			dragBlockHorizontal: true,
			swipeVelocityX: 0.4,
			swipeVelocityY: 0.3
	};

	$("#reserveButtonForm").submit(function() {

		var dt = moment().format('YYYY-MM-DD');
		if(calendar.selectedDate){
			dt = calendar.selectedDate;
		}
		location.href = "dayview.ftl?dt=" + dt;

		return false; // avoid to execute the actual submit of the form.
	});

	// "Reserve Multiple" opens the availability grid for the same day.
	$("#reserve-multiple-button").click(function() {
		var dt = moment().format('YYYY-MM-DD');
		if(calendar.selectedDate){
			dt = calendar.selectedDate;
		}
		location.href = "reservemulti.ftl?dt=" + dt;
	});
	
	
}(jQuery));