# Alfresco Dropbox Connector

This is built on an All-In-One (AIO) project for Alfresco SDK 3.0. 

Run project with `mvn clean install -DskipTests=true alfresco:run` or `./run.sh` and verify that it runs.

This project was built with the intention of extending an existing
 integration with Dropbox that due to limitations of the project, 
 could no longer function in any capacity (Dropbox's v1 API 
 was retired and the original project was built off of an sdk that utilized v1 endpoints).   
 
# Important To Note

 * Runs using Community assets (**Must** use Alfresco Community 5.1 or higher)
 * Can use Enterprise as well, just needs changes in the main project pom file
 * Does not yet actively check for updates between Alfresco and Dropbox (needs to be manually updated from Alfresco using document actions) 
 * Uses [Dropbox's Java SDK](https://www.dropbox.com/developers/documentation/java) to connect and make API calls
 * Expands on and updates original integration [found here](https://github.com/Alfresco/alfresco-dropbox-integration), but now supports interaction with Dropbox v2 API
 
# TODO
 
  * Fix/Update Polling
  * Extensive testing
  * Dropbox "App" it is connected to needs to be put to "Production"
  * Clean up/Polish code and remove unused classes
  * Enhance Readme to include a breakdown of features
  * Change Authentication so redirects aren't used, and instead codes are used
  
 
Licensed under the Apache License 2.0   
  
 
