
<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">
function main()
{
   // Call the repo to retrieve dropbox properties
   var result = remote.call("/dropbox/account/complete/workflow?verifier="+args.code);

   if (result.status == 200)
   {
      var auth = eval('(' + result + ')');
      
      model.success = auth.success;
   }
}

main();