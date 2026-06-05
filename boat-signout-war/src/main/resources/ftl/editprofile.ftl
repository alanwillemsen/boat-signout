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
	                <h1>Profile</h1>
	            </div>
	            
                <div class="row">
                <#assign picSrc = pictureUrl!'img/rower.png'><img src="${picSrc?starts_with('http')?string(picSrc, '/' + picSrc)}" style="padding:2px; width:150px; height:150px; border-radius:50%; object-fit:cover;"/>
                </div>
	            
                <div class="row">
                   <div class="span12">
                        <form id="profilePictureForm" action="/picture" method="post" enctype="multipart/form-data">
                            <fieldset>
                                <div class="clearfix">
                                    <label for="email">Upload new picture</label>

                                    <div class="input">
                                    	<input type="file" name="${username!''}">
                                    </div>
                                </div>
                            </fieldset>
                            <div class="actions">
                                <button type="submit" class="btn primary">Upload</button>
                            </div>
                        </form>
                    </div>
                </div>
                
                <br>
	            
                <div class="row">
                   <div class="span12">
                        <form id="settingsForm" action="${userBaseUrl}/settings/profile" method="post">
                            <fieldset>
                            
                                <div class="clearfix">
                                    <label for="displayName">Name</label>
                                    <div class="input">
                                        <input class="xlarge" id="displayName" name="displayName" size="30" type="text" value="${(displayName?html)!''}"/>
                                    </div>
                                </div>
                                <br>
                                <div class="clearfix">
                                    <label for="contactInfo">Contact information displayed to other members</label>
                                    <div class="input">
                                        <textarea class="xlarge" id="contactInfo" name="contactInfo" style="width: 100%; height: 150px;">${(contactInfo?html)!''}</textarea>
                                    </div>
                                </div>
                                 <br>
                                <div class="clearfix">
                                    <label for="displayName">Rowing background</label>
                                    <div class="input">
                                        <textarea class="xlarge" id="bio" name="bio" style="width: 100%; height: 150px;">${(bio?html)!''}</textarea>
                                    </div>
                                </div>
                                 <br>
                            </fieldset>
                            <div class="actions">
                                <button type="submit" class="btn primary">Update</button>
                            </div>
                        </form>
                    </div>
                </div>
                <div class="row" id="stage3" style="display:none">
                    <div class="span12">
                      <p>Your settings have been saved</p>
                    </div>
                </div>
        </section>
    </div>
<br>
<#include "inc/footer.ftl">
</div>

<#include "inc/_foot.ftl">
<script src="${staticBaseUrl}js/lib/jquery.linkify.min.js"></script>
<script>
$(document).ready(function() {
    $("#settingsForm").submit(function(e) {
        e.preventDefault();
        var form = $(this);
        var displayName = $("#displayName").val();
        var contactInfo = $("#contactInfo").val();
        var bio = $("#bio").val();
        $.ajax(shiro.userBaseUrl+"/settings/profile", {
            type: "POST",
            data: {
                displayName: displayName,
                bio: bio,
                contactInfo: contactInfo,
                username: "${username!''}"
            },
            dataType: "json",
            success: function(data, status) {
                if (status == 'success') {
                	shiro.status.clearStatus();
                    $("#stage3").show();
                }  else {
                    bootbox.alert("settings failed: " + data.message);
                }
            },
            error: function(xhr) {
                bootbox.alert("settings failed: " + xhr.responseText);
            }
        });
        return false;
    });
});
</script>


</body>
</html>