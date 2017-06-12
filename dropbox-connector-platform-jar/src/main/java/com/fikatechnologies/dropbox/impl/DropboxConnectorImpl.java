
/*
 * Copyright 2011-2012 Alfresco Software Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is part of an unsupported extension to Alfresco.
 *
 */
package com.fikatechnologies.dropbox.impl;

import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.v2.users.SpaceUsage;
import com.fikatechnologies.dropbox.DropboxConnector;
import net.sf.jmimemagic.*;
import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.exceptions.DropboxClientException;
import org.alfresco.dropbox.exceptions.FileNotFoundException;
import org.alfresco.dropbox.exceptions.FileSizeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.service.cmr.oauth2.OAuth2CredentialsStoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

/**
 * @author Sean Dumont
 * Changes made to update to the Dropbox provided SDK which support v2 of the API and oauth2 support.
 * Name change to reflect the significant changes to the class and methods, though most method names are the same to reduce impact to overall code
 * Original Author of DropboxServiceImpl below
 * @author Jared Ottley
 */
public class DropboxConnectorImpl implements DropboxConnector
{
	private static Log logger = LogFactory.getLog(DropboxConnectorImpl.class);

	private PersonService personService;
	private NodeService nodeService;
	private PermissionService permissionService;
	private ContentService contentService;
	private SysAdminParams sysAdminParams;
	private AuthorityService authorityService;
	private SiteService siteService;
	private BehaviourFilter behaviourFilter;
	private OAuth2CredentialsStoreService oauth2CredentialsStoreService;
	private DropboxClientFactory dropboxClientFactory;
	private NamespaceService namespaceService;
	private TenantService tenantService;

	public void setPersonService(PersonService personService)
	{
		this.personService = personService;
	}


	public void setNodeService(NodeService nodeService)
	{
		this.nodeService = nodeService;
	}


	public void setContentService(ContentService contentService)
	{
		this.contentService = contentService;
	}


	public void setPermissionService(PermissionService permissionService)
	{
		this.permissionService = permissionService;
	}


	public void setSysAdminParams(SysAdminParams sysAdminParams)
	{
		this.sysAdminParams = sysAdminParams;
	}


	public void setAuthorityService(AuthorityService authorityService)
	{
		this.authorityService = authorityService;
	}


	public void setSiteService(SiteService siteService)
	{
		this.siteService = siteService;
	}


	public void setBehaviourFilter(BehaviourFilter behaviourFilter)
	{
		this.behaviourFilter = behaviourFilter;
	}

	public void setOauth2CredentialsStoreService(OAuth2CredentialsStoreService oauth2CredentialsStoreService)
	{
		this.oauth2CredentialsStoreService = oauth2CredentialsStoreService;
	}

	public void setDropboxClientFactory(DropboxClientFactory dropboxClientFactory){
		this.dropboxClientFactory = dropboxClientFactory;
	}

	public void setNamespaceService(NamespaceService namespaceService){
		this.namespaceService = namespaceService;
	}

	public void setTenantService(TenantService tenantService){
		this.tenantService = tenantService;
	}

	private DbxClientV2 getClient() {
		DbxClientV2 client = null;

		String accessToken;

		OAuth2CredentialsInfo credsInfo = getTokenFromUser();

		if(credsInfo!=null) {
			accessToken = credsInfo.getOAuthAccessToken();

			client = dropboxClientFactory.createClient(accessToken);
		}

		return client;
	}

	@Override
	public String getAuthorizeUrl(String callbackUrl, DbxSessionStore csrfTokenStore){
		String authorizeUrl = null;
		if(callbackUrl != null){

			DbxWebAuth.Request authRequest = DbxWebAuth.newRequestBuilder()
					.withRedirectUri(callbackUrl, csrfTokenStore).build();

			persistTokens("", false);

			authorizeUrl = dropboxClientFactory.getDbxWebAuth().authorize(authRequest);
		}
		return authorizeUrl;
	}

	@Override
	public boolean completeAuthentication(String callbackUrl, DbxSessionStore csrfTokenStore, Map<String,String[]> request){
		boolean authenticationComplete = false;
		if(csrfTokenStore != null){
			NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());

			if(nodeService.hasAspect(person, DropboxConstants.Model.ASPECT_DROBOX_OAUTH)){
				DbxAuthFinish authFinish;
				try {
					authFinish = dropboxClientFactory.getDbxWebAuth().finishFromRedirect(callbackUrl, csrfTokenStore, request);
				} catch (DbxWebAuth.BadRequestException ex) {
					logger.error("On /dropbox-auth-finish: Bad request: " + ex.getMessage());
					//response.sendError(400);
					return false;
				} catch (DbxWebAuth.BadStateException ex) {
					// Send them back to the start of the auth flow.
					logger.error("Could node find state: "+ex.getMessage());
					//response.sendRedirect("http://my-server.com/dropbox-auth-start");
					return false;
				} catch (DbxWebAuth.CsrfException ex) {
					logger.error("On /dropbox-auth-finish: CSRF mismatch: " + ex.getMessage());
					//response.sendError(403, "Forbidden.");
					return false;
				} catch (DbxWebAuth.NotApprovedException ex) {
					// When Dropbox asked "Do you want to allow this app to access your
					logger.error("App not approved: "+ ex.getMessage());
					// Dropbox account?", the user clicked "No".
					return false;
				} catch (DbxWebAuth.ProviderException ex) {
					logger.error("On /dropbox-auth-finish: Auth failed: " + ex.getMessage());
					//response.sendError(503, "Error communicating with Dropbox.");
					return false;
				} catch (DbxException ex) {
					logger.error("On /dropbox-auth-finish: Error getting token: " + ex.getMessage());
					//response.sendError(503, "Error communicating with Dropbox.");
					return false;
				}
				String accessToken = authFinish.getAccessToken();

				persistTokens(accessToken, true);

				nodeService.setProperty(person, DropboxConstants.Model.PROP_OAUTH_COMPLETE, true);

				authenticationComplete = true;
			}
		}
		return authenticationComplete;
	}

	private OAuth2CredentialsInfo getTokenFromUser(){
		return oauth2CredentialsStoreService.getPersonalOAuth2Credentials(DropboxConstants.REMOTE_SYSTEM);
	}

	private void persistTokens(String accessToken, boolean complete){
		OAuth2CredentialsInfo credsInfo = getTokenFromUser();

		credsInfo = oauth2CredentialsStoreService.storePersonalOAuth2Credentials(DropboxConstants.REMOTE_SYSTEM, accessToken, null, null, null);

		if(credsInfo !=null){
			HashMap<QName, Serializable> properties = new HashMap<>();
			properties.put(DropboxConstants.Model.PROP_OAUTH_COMPLETE, complete);

			NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());
			nodeService.addAspect(person, DropboxConstants.Model.ASPECT_DROBOX_OAUTH, properties);
		}
	}

	@Override
	public FullAccount getUserProfile(){
		FullAccount fullAccount = null;
		DbxClientV2 clientV2 = this.getClient();
		try {
			fullAccount = clientV2.users().getCurrentAccount();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fullAccount;
	}

	@Override
	public SpaceUsage getUserSpaceUsage(){
		SpaceUsage spaceUsage = null;
		DbxClientV2 clientV2 = this.getClient();
		try {
			spaceUsage = clientV2.users().getSpaceUsage();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return spaceUsage;
	}

	public ListFolderResult getSpace(String path){
		ListFolderResult listFolderResult = null;
		DbxClientV2 clientV2 = this.getClient();

		try {
			listFolderResult = clientV2.files().listFolder(path);
		} catch (DbxException e) {
			e.printStackTrace();
		}

		return listFolderResult;
	}

	@Override
	public Metadata getMetadata(NodeRef nodeRef) {

		//Changed due to getDropboxPath method changes
		String path = getDropboxPath(nodeRef) /*+ "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)*/;

		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();
		try {
			metadata = clientV2.files().getMetadata(path);
		} catch (Exception e) {
			e.printStackTrace();
		}

		logger.debug("Get Metadata for " + path + ": " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata copy(NodeRef originalNodeRef, NodeRef newNodeRef){
		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();

		//Changed due to getDropboxPath method changes
		String from_path = getDropboxPath(originalNodeRef) /*+ "/" + nodeService.getProperty(originalNodeRef, ContentModel.PROP_NAME)*/;
		String to_path = getDropboxPath(newNodeRef) /*+ "/" + nodeService.getProperty(originalNodeRef, ContentModel.PROP_NAME)*/;

		try {
			metadata = clientV2.files().copyBuilder(from_path, to_path).withAllowSharedFolder(true).withAutorename(false).start();
		}catch(RelocationErrorException e){
			if(e.errorValue.getToValue().isConflict()){
				if(e.errorValue.getToValue().getConflictValue().toString().equalsIgnoreCase("file")){
					try{
						metadata = this.getMetadata(newNodeRef);
					}catch(Exception ee){
						ee.printStackTrace();
					}
				}else{
					e.printStackTrace();
				}
			}else{
				e.printStackTrace();
			}
		}catch(DbxException dbe){
			dbe.printStackTrace();
		}

		logger.debug("Copy " + from_path + " to " + to_path + ". New Metadata: " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata createFolder(NodeRef nodeRef){
		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();
		logger.trace("Normal path: "+nodeService.getPath(nodeRef).toString()+" \nTo Display Path: "
				+nodeService.getPath(nodeRef).toDisplayPath(nodeService, permissionService)+" \nTo Prefixed Path: "
				+nodeService.getPath(nodeRef).toPrefixString(namespaceService));
		//Changed due to getDropboxPath method changes
		String path = getDropboxPath(nodeRef) /*+ "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)*/;

		try {
			metadata = clientV2.files().createFolder(path);
		} catch (CreateFolderErrorException cfee) {
			//Added so if the folder creation fails and it's because a folder already exists, just add the metadata
			if(cfee.errorValue.getPathValue().isConflict()) {
				WriteConflictError wce = cfee.errorValue.getPathValue().getConflictValue();
				if(wce.toString().equalsIgnoreCase("folder")){
					try{
						metadata = this.getMetadata(nodeRef);
					}catch (Exception e){
						e.printStackTrace();
					}
				}
			} else{
				cfee.printStackTrace();
			}

		} catch (DbxException e) {
			e.printStackTrace();
		}

		logger.debug("Create Folder at "+ path + ". New Metadata: " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata delete(NodeRef nodeRef){
		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();

		//Changed due to getDropboxPath method changes
		String path = getDropboxPath(nodeRef) /*+ "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)*/;

		try
		{
			try {
				metadata = clientV2.files().delete(path);
			} catch (Exception e) {
				e.printStackTrace();
			}

			logger.debug("Delete " + path + ". Deleted Metadata: " + this.metadataAsJSON(metadata));
		}
		catch (RestClientException rce)
		{
			if (rce.getMessage().equals("404 Not Found"))
			{
				throw new FileNotFoundException(nodeRef);
			}
			else
			{
				throw new DropboxClientException(rce.getMessage());
			}
		}

		return metadata;
	}

	@Override
	public Metadata delete(String path){
		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();

		try
		{
			try {
				metadata = clientV2.files().delete(path);
			} catch (Exception e) {
				e.printStackTrace();
			}

			logger.debug("Delete " + path + ". Deleted Metadata: " + this.metadataAsJSON(metadata));
		}
		catch (RestClientException rce)
		{
			if (rce.getMessage().equals("404 Not Found"))
			{
				throw new FileNotFoundException();
			}
			else
			{
				throw new DropboxClientException(rce.getMessage());
			}
		}

		return metadata;
	}

	@Override
	public Metadata move(ChildAssociationRef oldChildAssocRef, ChildAssociationRef newChildAssocRef){
		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();

		//Small change to this so paths stay accurate
		String from_path = getDropboxPath(oldChildAssocRef.getParentRef()) + "/"
				+ nodeService.getProperty(oldChildAssocRef.getChildRef(), ContentModel.PROP_NAME);

		String to_path = getDropboxPath(newChildAssocRef.getParentRef()) + "/"
				+ nodeService.getProperty(newChildAssocRef.getChildRef(), ContentModel.PROP_NAME);

		try {
			metadata = clientV2.files().move(from_path, to_path);
		} catch (Exception e) {
			e.printStackTrace();
		}

		logger.debug("Move " + from_path + " to " + to_path + ". New Metadata: " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata getFile(NodeRef nodeRef){
		Metadata metadata;
		DbxClientV2 clientV2 = this.getClient();

		//Changed due to getDropboxPath method changes
		String path = getDropboxPath(nodeRef) /*+ "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)*/;

		DbxDownloader<FileMetadata> dropboxFile = null;
		try {
			dropboxFile = clientV2.files().download(path);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try
		{
			ContentWriter writer = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
			writer.guessEncoding();
			if (dropboxFile != null) {
				InputStream stream = dropboxFile.getInputStream();
				//getting the mimetype from the input stream byte array
				MagicMatch matcher = null;
				try {
					matcher = Magic.getMagicMatch(IOUtils.toByteArray(stream));
				} catch (MagicParseException | MagicMatchNotFoundException | IOException | MagicException e) {
					e.printStackTrace();
				}
				if (matcher != null) {
					String mimetype = matcher.getMimeType();
					writer.setMimetype(mimetype);
				}
				writer.putContent(dropboxFile.getInputStream());
			}else {
				return null;
			}
		}
		catch (ContentIOException cio)
		{
			cio.printStackTrace();
		}

		metadata = this.getMetadata(nodeRef);

		logger.debug("Get File " + path + ". File Metadata: " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata putFile(NodeRef nodeRef, boolean overwrite){
		// 150 MB
		final long MAX_FILE_SIZE = 157286400L;

		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();

		ContentReader contentReader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

		if (contentReader.getSize() < MAX_FILE_SIZE)
        {
			//Changed due to getDropboxPath method changes
            String path = getDropboxPath(nodeRef) /*+ "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)*/;
            try {
                metadata = clientV2.files().upload(path).uploadAndFinish(contentReader.getContentInputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else
        {
            throw new FileSizeException();
        }

		logger.debug("Put File " + getDropboxPath(nodeRef) /*+ "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)*/
				+ ". File Metadata " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public void persistMetadata(Metadata md, NodeRef nodeRef)
	{

		List<ChildAssociationRef> dropboxAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

		ChildAssociationRef usersAssocRef;
		if (dropboxAssoc.size() == 0)
		{
			usersAssocRef = nodeService.createNode(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.TYPE_USERS);
		}
		else
		{
			usersAssocRef = dropboxAssoc.get(0);
		}

		List<ChildAssociationRef> usersAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

		ChildAssociationRef userAssocRef;
		if (usersAssoc.size() == 0)
		{
			userAssocRef = nodeService.createNode(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()), DropboxConstants.Model.TYPE_METADATA);
		}
		else
		{
			userAssocRef = usersAssoc.get(0);
		}

		Map<QName, Serializable> properties = new HashMap<>();
		if(md instanceof FolderMetadata) {
			if(nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER)){
				behaviourFilter.disableBehaviour(userAssocRef.getChildRef(), ContentModel.ASPECT_AUDITABLE);
				nodeService.addProperties(userAssocRef.getChildRef(), properties);
			}
		}else if(md instanceof FileMetadata){
			FileMetadata metadata = (FileMetadata) md;
			properties.put(DropboxConstants.Model.PROP_REV, metadata.getRev());
			// properties.put(DropboxConstants.Model.PROP_REVISION, metadata.get)
			properties.put(ContentModel.PROP_MODIFIED, metadata.getServerModified());
			properties.put(DropboxConstants.Model.PROP_HASH, metadata.getContentHash());
			behaviourFilter.disableBehaviour(userAssocRef.getChildRef(), ContentModel.ASPECT_AUDITABLE);
			nodeService.addProperties(userAssocRef.getChildRef(), properties);
		}

	}

	@Override
	public Map<QName, Serializable> getPersistedMetadata(NodeRef nodeRef)
	{

		Map<QName, Serializable> properties = null;
		List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

		ChildAssociationRef usersAssocRef;
		if (childAssoc.size() > 0)
		{
			usersAssocRef = childAssoc.get(0);

			List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

			if (userAssoc.size() == 0)
			{
				throw new DropboxClientException("No Metadata for this User");
			}
			else
			{
				properties = new HashMap<>();
				properties.put(DropboxConstants.Model.PROP_REV, nodeService.getProperty(userAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_REV));
				properties.put(ContentModel.PROP_MODIFIED, nodeService.getProperty(userAssoc.get(0).getChildRef(), ContentModel.PROP_MODIFIED));

				if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
				{
					properties.put(DropboxConstants.Model.PROP_HASH, nodeService.getProperty(userAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_HASH));
				}
			}
		}

		return properties;
	}

	@Override
	public boolean deletePersistedMetadata(NodeRef nodeRef)
	{
		boolean deleted = false;
		List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

		if (childAssoc.size() > 0)
		{
			ChildAssociationRef usersAssocRef = childAssoc.get(0);

			List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

			if (userAssoc.size() > 0)
			{
				deleted = nodeService.removeChildAssociation(userAssoc.get(0));
			}

			if (getSyncCount(nodeRef) == 0)
			{
				nodeService.removeChildAssociation(childAssoc.get(0));
				nodeService.removeAspect(nodeRef, DropboxConstants.Model.ASPECT_DROPBOX);
			}
		}

		return deleted;
	}

	@Override
	public boolean deletePersistedMetadata(final NodeRef nodeRef, String userAuthority)
	{
		boolean deleted = false;

		SiteInfo siteInfo = siteService.getSite(nodeRef);
		if (siteInfo != null)
		{
			if (authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())
					|| siteService.getMembersRole(siteInfo.getShortName(), AuthenticationUtil.getRunAsUser()).equals("SiteManager"))
			{
				deleted = AuthenticationUtil.runAs(() -> deletePersistedMetadata(nodeRef), userAuthority);
			}
		}

		return deleted;
	}

	/**
	 * All the user metadata for the synched users. User must be Repository Admin or Site Manager.
	 *
	 * @param nodeRef Node ref to check synced users on
	 * @return syncedUsers
	 */
	@Override
	public Map<String, NodeRef> getSyncedUsers(NodeRef nodeRef)
	{
		Map<String, NodeRef> syncedUsers = new HashMap<>();

		if (nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_DROPBOX))
		{
			List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

			if (childAssoc.size() > 0)
			{
				ChildAssociationRef usersAssocRef = childAssoc.get(0);

				Set<QName> childNodeTypeQNames = new HashSet<>();
				childNodeTypeQNames.add(DropboxConstants.Model.TYPE_METADATA);
				List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), childNodeTypeQNames);

				if (userAssoc.size() > 0)
				{
					for (ChildAssociationRef childAssociationRef : userAssoc) {
						syncedUsers.put(childAssociationRef.getQName().getLocalName(), childAssociationRef.getChildRef());
					}
				}
			}
		}

		return syncedUsers;
	}

	/**
	 * Is the node synced to Dropbox for the currently authenticated User
	 *
	 * @param nodeRef Node ref to check if is synced to current user
	 * @return synced
	 */
	@Override
	public boolean isSynced(NodeRef nodeRef)
	{
		boolean synced = false;

		List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

		ChildAssociationRef usersAssocRef;
		if (childAssoc.size() == 0)
		{
			usersAssocRef = nodeService.createNode(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.TYPE_USERS);
		}
		else
		{
			usersAssocRef = childAssoc.get(0);
		}

		List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

		if (userAssoc.size() > 0)
		{
			synced = true;
		}

		return synced;
	}

	/**
	 * Total number of users who have the node synced to their Dropbox account. If -1 is returned then the total could not be
	 * determined.
	 *
	 * @param nodeRef Node Ref to get sync count on
	 * @return count
	 */
	private int getSyncCount(final NodeRef nodeRef){
		int count = -1;

		AuthenticationUtil.runAs(() -> {
            int count1 = -1;

            List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

            if(childAssoc.size() > 0){
                ChildAssociationRef usersAssocRef = childAssoc.get(0);

                Set<QName> childNodeTypeQnames = new HashSet<>();
                childNodeTypeQnames.add(DropboxConstants.Model.ASSOC_USER_METADATA);
                List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), childNodeTypeQnames);

                if(userAssoc.size() >= 0){
                    count1 = userAssoc.size();
                }
            }
            return count1;
        }, AuthenticationUtil.getAdminUserName());
		return count;
	}

	@Override
	public String getDropboxPath(NodeRef nodeRef){

		Iterator<Path.Element> paths = nodeService.getPath(nodeRef).iterator();
		StringBuilder pathBuilder= new StringBuilder();
		while(paths.hasNext()){
			Path.Element pelement = paths.next();
			if(!pelement.getPrefixedString(namespaceService).equalsIgnoreCase("/")) {
				QName elementName = QName.createQName(pelement.getPrefixedString(namespaceService), namespaceService);
				if(!paths.hasNext()){
					pathBuilder.append(elementName.getLocalName());
				}else {
					pathBuilder.append(elementName.getLocalName()).append("/");
				}
			}
		}

		String path = pathBuilder.toString();
		//Changed this since toDisplayPath is not accurate
		//String path = nodeService.getPath(nodeRef).toDisplayPath(nodeService, permissionService);
		//path = path.replaceFirst(DropboxConstants.COMPANY_HOME, sysAdminParams.getShareHost());
		path = path.replaceFirst(DropboxConstants.COMPANY_HOME_LOCAL, sysAdminParams.getShareHost());
		path = path.replaceFirst(DropboxConstants.DOCUMENTLIBRARY, "");

		logger.debug("Path: "+ path);

		return "/"+path;
	}

	private String metadataAsJSON(Metadata metadata){
		String json;
		if(metadata!=null) {
			if (metadata instanceof FileMetadata) {
				FileMetadata fileMetadata = (FileMetadata) metadata;
				json = "{ \"size\": " + fileMetadata.getSize() + ", \"is_dir\": " + false + ", \"is_deleted\": " + false + ", \"rev\": \""
						+ fileMetadata.getRev() + "\", \"hash\": \"" + fileMetadata.getContentHash() + "\", \"modified\": \""
						+ fileMetadata.getServerModified() + "\", \"path\": \"" + fileMetadata.getPathDisplay() + "\"}";
			} else if (metadata instanceof FolderMetadata) {
				FolderMetadata folderMetadata = (FolderMetadata) metadata;
				json = "{ \"name\": \"" + folderMetadata.getName() + "\", \"path\": \"" + folderMetadata.getPathDisplay() + "\"}";
			} else {
				throw new IllegalArgumentException("Metadata supplied was neither folder metadata or file metadata");
			}
		}else{
			throw new NullPointerException(" JSON Metadata was null due to earlier error");
		}
		return json;
	}
}
