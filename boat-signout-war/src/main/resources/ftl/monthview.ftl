<#assign style="mainstyle.css">
<!DOCTYPE html>
<html lang="en" class="shiro-none-active">
<head>
    <#include "inc/_head.ftl">
</head>

<body>

	<#include "inc/topbar.ftl">
	
	<div class="container">

		<div class="page-header">

			<div class="pull-right form-inline">
				<div class="btn-group">
					<form id="reserveButtonForm">
						<button type="submit" class="btn btn-success" id="reserve-button">Reserve a Boat</button>
					</form>
				</div>
				<div class="btn-group">
					<button type="button" class="btn btn-primary" id="reserve-multiple-button">Reserve Multiple</button>
				</div>
				<div class="btn-group">
					<button class="btn btn-info" data-calendar-nav="prev">&lt;&lt; Prev</button>
					<button class="btn btn-default" data-calendar-nav="today">Today</button>
					<button class="btn btn-info" data-calendar-nav="next">Next &gt;&gt;</button>
				</div>
				<!-- div class="btn-group">
					<button class="btn btn-warning" data-calendar-view="year">Year</button>
					<button class="btn btn-warning active" data-calendar-view="month">Month</button>
					<button class="btn btn-warning" data-calendar-view="week">Week</button>
					<button class="btn btn-warning" data-calendar-view="day">Day</button>
				</div-->
			</div>

			<h3></h3>
		</div>

		<div class="row">

			<div class="col-md-12">
				<div id="calendar"></div>
			</div>


		</div>

		<div class="clearfix"></div>
		<br> <br>
		
		<#include "inc/footer.ftl">
	</div>
		<#include "inc/_foot.ftl">
		<script type="text/javascript" src="js/app.js?v=20260607-2"></script>
</body>
</html>
