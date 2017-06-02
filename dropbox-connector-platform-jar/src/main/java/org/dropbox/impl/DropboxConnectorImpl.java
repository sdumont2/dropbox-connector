
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
package org.dropbox.impl;

import com.dropbox.core.*;
import com.dropbox.core.http.OkHttp3Requestor;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.Metadata;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.oauth2.OAuth2CredentialsStoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dropbox.DropboxConnector;
import org.dropbox.DropboxConstants;
import org.dropbox.exceptions.DropboxClientException;
import org.dropbox.exceptions.FileNotFoundException;
import org.dropbox.exceptions.FileSizeException;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

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
	private OAuth2CredentialsStoreService oAuth2CredentialsStoreService;
	private final String appKey = "a4yx43gwuxvrtsm";
	private final String appSecret = "mwnu3vea9myezhr";

	private DbxClientV2 client;
	private final DbxAppInfo dbxAppInfo = new DbxAppInfo(appKey, appSecret);
	private final DbxRequestConfig dbxRequestConfig = DbxRequestConfig.newBuilder("alfresco/2.0")
			.withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
			.build();
	private final DbxWebAuth dbxWebAuth = new DbxWebAuth(dbxRequestConfig, dbxAppInfo);

	@Override
	public DbxClientV2 getClient() {
		if (client == null) {
			String accessToken;
			OAuth2CredentialsInfo credsInfo = getTokenFromUser();
			if(credsInfo!=null) {
				accessToken = credsInfo.getOAuthAccessToken();

				client = new DbxClientV2(dbxRequestConfig, accessToken);
			}
		}
		return client;
	}

	@Override
	public String getAuthorizeUrl(String callbackUrl, DbxSessionStore csrfTokenStore){
		String authorizeUrl = null;
		if(callbackUrl != null){

			DbxWebAuth.Request authRequest = DbxWebAuth.newRequestBuilder()
					.withRedirectUri(callbackUrl, csrfTokenStore).build();

			authorizeUrl = dbxWebAuth.authorize(authRequest);
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
					authFinish = dbxWebAuth.finishFromRedirect(callbackUrl, csrfTokenStore, request);
				} catch (DbxWebAuth.BadRequestException ex) {
					logger.error("On /dropbox-auth-finish: Bad request: " + ex.getMessage());
					//response.sendError(400);
					return false;
				} catch (DbxWebAuth.BadStateException ex) {
					// Send them back to the start of the auth flow.
					//response.sendRedirect("http://my-server.com/dropbox-auth-start");
					return false;
				} catch (DbxWebAuth.CsrfException ex) {
					logger.error("On /dropbox-auth-finish: CSRF mismatch: " + ex.getMessage());
					//response.sendError(403, "Forbidden.");
					return false;
				} catch (DbxWebAuth.NotApprovedException ex) {
					// When Dropbox asked "Do you want to allow this app to access your
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
		return oAuth2CredentialsStoreService.getPersonalOAuth2Credentials(DropboxConstants.REMOTE_SYSTEM);
	}

	private void persistTokens(String accessToken, boolean complete){
		OAuth2CredentialsInfo credsInfo = getTokenFromUser();

		credsInfo = oAuth2CredentialsStoreService.storePersonalOAuth2Credentials(DropboxConstants.REMOTE_SYSTEM, accessToken, null, null, null);

		if(credsInfo !=null){
			HashMap<QName, Serializable> properties = new HashMap<>();
			properties.put(DropboxConstants.Model.PROP_OAUTH_COMPLETE, complete);

			NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());
			nodeService.addAspect(person, DropboxConstants.Model.ASPECT_DROBOX_OAUTH, properties);
		}
	}

	@Override
	public Metadata getMetadata(NodeRef nodeRef) {
		String hash = null;

		//get the hash if it exists
		if(nodeService.getProperty(nodeRef, DropboxConstants.Model.PROP_HASH) != null){
			hash = nodeService.getProperty(nodeRef, DropboxConstants.Model.PROP_HASH).toString();
		}

		String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

		NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());

		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();
		try {
			metadata = clientV2.files().getMetadata(path);
		} catch (DbxException e) {
			e.printStackTrace();
		}

		logger.debug("Get Metadata for " + path + ": " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata copy(NodeRef originalNodeRef, NodeRef newNodeRef){
		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();

		String from_path = getDropboxPath(originalNodeRef) + "/" + nodeService.getProperty(originalNodeRef, ContentModel.PROP_NAME);
		String to_path = getDropboxPath(newNodeRef) + "/" + nodeService.getProperty(newNodeRef, ContentModel.PROP_NAME);

		try {
			metadata = clientV2.files().copy(from_path, to_path);
		} catch (DbxException e) {
			e.printStackTrace();
		}

		logger.debug("Copy " + from_path + " to " + to_path + ". New Metadata: " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata createFolder(NodeRef nodeRef){
		Metadata metadata = null;
		DbxClientV2 clientV2 = this.getClient();

		String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

		try {
			metadata = clientV2.files().createFolder(path);
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

		String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

		try
		{
			try {
				metadata = clientV2.files().delete(path);
			} catch (DbxException e) {
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
			} catch (DbxException e) {
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

		String from_path = getDropboxPath(oldChildAssocRef.getParentRef()) + "/"
				+ nodeService.getProperty(oldChildAssocRef.getChildRef(), ContentModel.PROP_NAME);
		String to_path = getDropboxPath(newChildAssocRef.getChildRef()) + "/"
				+ nodeService.getProperty(newChildAssocRef.getChildRef(), ContentModel.PROP_NAME);

		try {
			metadata = clientV2.files().move(from_path,to_path);
		} catch (DbxException e) {
			e.printStackTrace();
		}

		logger.debug("Move " + from_path + " to " + to_path + ". New Metadata: " + this.metadataAsJSON(metadata));

		return metadata;
	}

	@Override
	public Metadata getFile(NodeRef nodeRef){
		Metadata metadata=null;
		DbxClientV2 clientV2 = this.getClient();

		String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

		DbxDownloader<FileMetadata> dropboxFile = null;
		try {
			dropboxFile = clientV2.files().download(path);
		} catch (DbxException e) {
			e.printStackTrace();
		}
		try
		{
			ContentWriter writer = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
			writer.guessEncoding();
			if (dropboxFile != null) {
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

		try
		{
			if (contentReader.getSize() < MAX_FILE_SIZE)
			{
				String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
				try {
					metadata = clientV2.files().upload(path).uploadAndFinish(contentReader.getContentInputStream());
				} catch (DbxException e) {
					e.printStackTrace();
				}
			}
			else
			{
				throw new FileSizeException();
			}
		}
		catch (IOException ioe)
		{
			throw new DropboxClientException(ioe.getMessage());
		}

		logger.debug("Put File " + getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)
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

		Map<QName, Serializable> properties = new HashMap<QName, Serializable>();
		if(md instanceof FolderMetadata) {
			if(nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER)){
				List<ChildAssociationRef> childAssocList= nodeService.getChildAssocs(nodeRef);
				for (ChildAssociationRef aChildAssocList : childAssocList) {
					NodeRef childRef = aChildAssocList.getChildRef();
					Metadata childMetadata = getMetadata(childRef);
					persistMetadata(childMetadata, childRef);
				}
			}
			behaviourFilter.disableBehaviour(userAssocRef.getChildRef(), ContentModel.ASPECT_AUDITABLE);
			nodeService.addProperties(userAssocRef.getChildRef(), properties);
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
				deleted = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>()
				{
					public Boolean doWork()
							throws Exception
					{
						return deletePersistedMetadata(nodeRef);
					}
				}, userAuthority);
			}
		}

		return deleted;
	}

	/**
	 * All the user metadata for the synched users. User must be Repository Admin or Site Manager.
	 *
	 * @param nodeRef
	 * @return
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
					for (Iterator<ChildAssociationRef> iterator = userAssoc.iterator(); iterator.hasNext();)
					{
						ChildAssociationRef childAssociationRef = iterator.next();
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
	 * @param nodeRef
	 * @return
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
	 * Is the node synced to Dropbox for the user. Requires Admin or SiteManager role to run
	 *
	 * @param nodeRef
	 * @param userAuthority
	 * @return
	 */
	public boolean isSynced(final NodeRef nodeRef, String userAuthority)
	{
		boolean synced = false;

		SiteInfo siteInfo = siteService.getSite(nodeRef);
		if (siteInfo != null)
		{
			if (authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())
					|| siteService.getMembersRole(siteInfo.getShortName(), AuthenticationUtil.getRunAsUser()).equals("SiteManager"))
			{
				synced = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>()
				{
					public Boolean doWork()
							throws Exception
					{
						return isSynced(nodeRef);
					}
				}, userAuthority);
			}
		}

		return synced;
	}

	/**
	 * Total number of users who have the node synced to their Dropbox account. If -1 is returned then the total could not be
	 * determined.
	 *
	 * @param nodeRef
	 * @return
	 */
	private int getSyncCount(final NodeRef nodeRef){
		int count = -1;

		AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Integer>() {
			public Integer doWork() throws Exception{
				int count = -1;

				List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

				if(childAssoc.size() > 0){
					ChildAssociationRef usersAssocRef = childAssoc.get(0);

					Set<QName> childNodeTypeQnames = new HashSet<>();
					childNodeTypeQnames.add(DropboxConstants.Model.ASSOC_USER_METADATA);
					List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), childNodeTypeQnames);

					if(userAssoc.size() >= 0){
						count = userAssoc.size();
					}
				}
				return count;
			}
		}, AuthenticationUtil.getAdminUserName());
		return count;
	}

	@Override
	public String getDropboxPath(NodeRef nodeRef){
		String path = nodeService.getPath(nodeRef).toDisplayPath(nodeService, permissionService);
		path = path.replaceFirst(DropboxConstants.COMPANY_HOME, sysAdminParams.getShareHost());
		path = path.replaceFirst(DropboxConstants.DOCUMENTLIBRARY, "");

		logger.debug("Path: "+ path);

		return path;
	}

	private byte[] getBytes(ContentReader reader) throws IOException{
		InputStream originalInputStream = reader.getContentInputStream();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		final int BUF_SIZE = 1 << 8; // 1KiB buffer
		byte[] buffer = new byte[BUF_SIZE];
		int bytesRead = -1;

		while((bytesRead = originalInputStream.read(buffer)) > -1){
			outputStream.write(buffer, 0, bytesRead);
		}

		originalInputStream.close();
		return outputStream.toByteArray();
	}

	private String metadataAsJSON(Metadata metadata){
		String json;
		if(metadata instanceof FileMetadata) {
			FileMetadata fileMetadata = (FileMetadata) metadata;
			json = "{ \"size\": " + fileMetadata.getSize() + ", \"is_dir\": " + false + ", \"is_deleted\": " + false + ", \"rev\": \""
					+ fileMetadata.getRev() + "\", \"hash\": \"" + fileMetadata.getContentHash() + "\", \"modified\": \""
					+ fileMetadata.getServerModified() + "\", \"path\": \"" + fileMetadata.getPathDisplay() + "\"}";
		}else if(metadata instanceof FolderMetadata){
			FolderMetadata folderMetadata = (FolderMetadata) metadata;
			json = "{ \"name\": \"" + folderMetadata.getName() + "\", \"path\": \"" + folderMetadata.getPathDisplay() + "\"}";
		}else{
			throw new IllegalArgumentException("Metadata supplied was neither folder metadata or file metadata");
		}
		return json;
	}

}
