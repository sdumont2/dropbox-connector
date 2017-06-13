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
 
# How to connect your account

 * After login screen go to your user profile
 * From user profile, there should be a tab called "dropbox"
 * On the Dropbox tab user's should click the "Start Authentication" button
 * A new tab will open in your browser with a login screen for your Dropbox credentials
  (if you're not logged in already, if you are, skip this step)
 * You will be asked to allow the connector to access your dropbox account
 * When you click allow, you will be given a code
 * Copy the code and paste it in your user profile and click the  "Link Account" button
 * This should link your account and refresh the page, giving you a view of your account stats
 (i.e. the amount of space available to you and how much you've used, etc.)
 
# How to send content to Dropbox

# TODO
 
  * Extensive testing
  * Dropbox "App" it is connected to needs to be put to "Production"
  * Clean up/Polish code and remove unused classes
  * Enhance Readme to include a breakdown of features and how to use them
  
 
Licensed under the Apache License 2.0   
  
 
