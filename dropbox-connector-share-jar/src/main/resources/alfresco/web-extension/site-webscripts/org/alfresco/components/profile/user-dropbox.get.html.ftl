<#include "../component.head.inc">
<@markup id="css">
  <@link rel="stylesheet" type="text/css" href="${url.context}/res/components/profile/userdropbox.css" />
  <@link href="${url.context}/res/components/profile/profile.css" group="profile"/>
</@>

<@markup id="js">
  <#-- JavaScript Dependencies -->
  <@script type="text/javascript" src="${url.context}/res/components/profile/userdropbox.js"></@script> 
</@>

<@markup id="widgets">
   <@createWidgets group="profile"/>
</@>

<@markup id="html">
  <@uniqueIdDiv>
    <#assign el=args.htmlid?html>
    <script type="text/javascript">//<![CDATA[
       new Alfresco.UserDropbox("${el}");
    //]]></script>
    <div id="${el}-body" class="dropbox">
    <div class="header-bar">Dropbox Account Details</div>
    <#if authenticated>
       
          <div class="row">
    	Account Name: ${display_name} <br />
              Quota: ${quota_string} <br />
              Quota Used: ${quota_normal_string} <br />
      Account Email: ${email} <br />
          </div>
          <hr/>
          <div class="row">
          <form id="${el}-form" action="${url.context}/service/components/profile/user-dropbox/delink" method="post">
    	<div class="buttons">
    	   <button id="${el}-dropbox-button" name="save">Delink Account</button>	
            </div>
          </form>
          </div>
    <#else>
            <div class="row">
                <div class="buttons">
                    <button id="${el}-dropbox-link" name="save">Start Account Authorization</button>
                </div>
                <form id="${el}-authform" action="${url.context}/service/components/profile/user-dropbox/link" method="post">
                    <div class="fields">
                        <label for="${el}-authcode">Authorization Code:</label><input id="${el}-authcode" type="text" name="authcode">
                    </div>
                <div class="buttons">
                   <button id="${el}-dropbox-auth" name="save">Link Account With Code</button>
                </div>
                </form>
            </div>
    </#if>
    </div>
  </@>
</@>
