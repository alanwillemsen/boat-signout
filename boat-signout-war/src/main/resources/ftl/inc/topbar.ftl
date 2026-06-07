	<nav class="navbar navbar-inverse" role="navigation">
		<div class="container-fluid">
			<!-- Brand and toggle get grouped for better mobile display -->
			<div class="navbar-header">
				<a class="navbar-brand" href="/">Cambridge Rowing Club Boat Reservation</a>
			</div>
			<div>
				<ul class="nav navbar-nav navbar-right shiro-guest">
					<li><a id="signIn" href="/">Sign In</a></li>
				</ul>
				<ul class="nav navbar-nav navbar-right shiro-user">
					<li><a id="monthviewLink" href="/monthview.ftl">Calendar</a></li>
					<li class="shiro-admin"><a id="membersLink" href="/users.ftl">Members</a></li>
					<li class="shiro-admin"><a id="boatsLink" href="/boats.ftl">Boats</a></li>
					<li><a id="profileLink" href="/profile">Profile</a></li>
					<li><a id="logout" href="/logout">Sign Out <span class="shiro-principal"></span></a></li>
				</ul>
			</div>
		</div>
	</nav>