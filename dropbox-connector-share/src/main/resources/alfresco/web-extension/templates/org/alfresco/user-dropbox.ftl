<#include "include/alfresco-template.ftl" />
<@templateHeader />

<@templateBody>
   <@markup id="alf-hd">
   <div id="alf-hd">
      <@region id="share-header" scope="global" chromeless="true" /> 
      <@region id="title" scope="page" />
   </div>
   </@>
   <@markup id="bd">
   <div id="bd">
      <@region id="toolbar" scope="page" />
      <@region id="user-dropbox" scope="page" />
   </div>
   </@>
</@>

<@templateFooter>
   <@markup id="alf-ft">
   <div id="alf-ft">
      <@region id="footer" scope="global" />
   </div>
   </@>
</@>
