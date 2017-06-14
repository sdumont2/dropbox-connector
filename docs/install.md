# Alfresco to Dropbox Connector Installation Guide

This is built on an All-In-One (AIO) project for Alfresco SDK 3.0. 

Run project with `mvn clean install -DskipTests=true alfresco:run` or `./run.sh` and verify that it runs.

This project was built with the intention of extending an existing
 integration with Dropbox that due to limitations of the project, 
 could no longer function in any capacity (Dropbox's v1 API 
 was retired and the original project was built off of an sdk that utilized v1 endpoints). 
 
## How to install into Alfresco Tomcat

  #### - Jar Method:
   * Build this Project by running `mvn clean install -DskipTests=true`
   * Place platform jar into `{AlfrescoDir}/modules/platform`
   * Place Share jar into `{AlfrescoDir}/modules/share`
   * Take files from the project's `libs` directory (there should be three `.jar` files:
    `jmimemagic-0.1.2.jar`, `jackson-core-2.8.3.jar`, `dropbox-core-sdk-3.0.3.jar`)
     and place them in the `tomcat/shared/lib` directory of your Alfresco directory.
   * Start Alfresco
  
  #### - AMP Method:
   * Build Project by running `mvn clean install -DskipTests=true` 
   * Place platform AMP into the `{AlfrescoDir}/amps` directory
   * Place share AMP into the `{AlfrescoDir}/amps_share` directory
   * Run `./apply_amps.sh -f` in the `{AlfrescoDir}/bin` directory
   * Start Alfresco
   
## How to connect your account

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
 
## How to send content to Dropbox