
<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">
function main()
{
   // Call the repo to retrieve dropbox properties
    //Change here to include state in params so auth doesn't fail
   var result = remote.call("/dropbox/account/complete/workflow?state="+args.state+"&code="+args.code);

   if (result.status == 200)
   {
      var auth = eval('(' + result + ')');
      
      model.success = auth.success;
   }
}

main();