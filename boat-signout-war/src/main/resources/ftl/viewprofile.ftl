<#assign style="mainstyle.css">
<!DOCTYPE html>
<html lang="en" class="shiro-none-active">
<head>
    <#include "inc/_head.ftl">
</head>

<body>

<div id="spinner" class="shiro-unset" style="position: absolute; top: 90px; left: 50%;">
</div>

<#include "inc/topbar.ftl">

<div class="container">


    <div class="page-header">
        <h1>${(displayName?html)!''}</h1>
    </div>
    <br>
    <p>
    	<img src="/${pictureUrl!'img/rower.png'}" style="padding:2px;"/>
    </p>
    
    <#if contactInfo??>
    <h3>Contact info</h3>
    <p>
   		<p id="contactInfo">${(contactInfo?html?replace("\n", "<br>"))!''}</p>
    </p>
    </#if>
    
    <#if bio??>
    <h3>Rowing background</h3>
    <p>
    	<p id="bio">${(bio?html?replace("\n", "<br>"))!''}</p>
    </p>
    </#if>
    

<br>
<#include "inc/footer.ftl">
</div>

<#include "inc/_foot.ftl">
<script src="${staticBaseUrl}js/lib/jquery.linkify.min.js"></script>
<script>
$(document).ready(function() {
    $('p').linkify();
});
</script>


</body>
</html>