# Alfresco Dropbox Connector

This is built on an All-In-One (AIO) project for Alfresco SDK 3.0. 

Run project with `mvn clean install -DskipTests=true alfresco:run` or `./run.sh` and verify that it runs.
 
# Few things to notice

 * Runs using Community assets (**Must** use Alfresco Community 4.0 or higher)
 * Can use Enterprise as well, just needs changes in the main project pom file
 * Does not yet actively check for updates between Alfresco and Dropbox (needs to be manually updated from Alfresco using document actions) 
 * Uses [Dropbox's Java SDK](https://www.dropbox.com/developers/documentation/java) to connect and make API calls
 * Expands on and updates original integration [found here](https://github.com/Alfresco/alfresco-dropbox-integration), but now supports interaction with Dropbox v2 API
 
# TODO
 
  * Fix/Update Polling
  * Extensive testing
  * Dropbox "App" it is connected to needs to be put to "Production"
  * Clean up/Polish code and remove unused classes
  
 
Licensed under the Apache License 2.0   
  
 
