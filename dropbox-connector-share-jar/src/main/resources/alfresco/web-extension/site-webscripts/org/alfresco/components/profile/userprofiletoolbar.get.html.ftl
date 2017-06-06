<@markup id="css" >
   <#-- CSS Dependencies -->
   <@link href="${url.context}/res/components/profile/toolbar.css" group="profile"/>
</@>

<@markup id="widgets">
   <@createWidgets group="profile"/>
</@>

<@markup id="html">
   <@uniqueIdDiv>
      <#assign activePage = page.url.templateArgs.pageid?lower_case!"">
      <#assign el=args.htmlid?js_string/>
         <div id="${args.htmlid}-body" class="toolbar userprofile">


            <#-- LINKS  changed to include all other links with the dropbox link appended to the end-->
         <@markup id="links">
         <div class="members-bar-links">
            <#list links as link>
               <div class="link">
                  <a id="${el}-${link.id}" href="${link.href}" class="${link.cssClass!""}">${link.label?html}</a>
               </div>
               <#if link_has_next>
                  <div class="separator">&nbsp;</div>
               </#if>
            </#list>
         </div>
         </@markup>

            <div class="separator">&nbsp;</div>
            <div class="link"><a href="user-dropbox" <#if activePage=="user-dropbox">class="activePage theme-color-4"</#if>>${msg("link.dropbox")}</a></div>
         </div>
   </@>
</@>