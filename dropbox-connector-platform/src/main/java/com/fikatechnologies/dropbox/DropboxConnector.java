
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
package com.fikatechnologies.dropbox;

import com.dropbox.core.DbxSessionStore;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.v2.users.SpaceUsage;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

import java.io.Serializable;
import java.util.Map;

/**
 * Any unnoted changes were to update code for usability with a new/different sdk
 * @author Sean Dumont
 * Original author of the DropboxService interface and class noted below
 * (DropboxConnector is new name based on significant amount of changes to implementing class).
 * @author Jared Ottley
 */
public interface DropboxConnector
{

	/**
	 * Get the oAuth2 Authorization Url for the current user
	 *	Changed to support Oauth2 and dropbox java sdk
	 * @param callbackUrl
	 * @param csrfTokenStore
	 * @return
	 */
	String getAuthorizeUrl(String callbackUrl, DbxSessionStore csrfTokenStore);

	/**
	 * Complete the oAuth2 Flow. Persists the returned tokens for the current User
	 *	Changed to support Oauth2 and dropbox java sdk
	 * @param callbackUrl
	 * @param csrfTokenStore
	 * @param request
	 * @return
	 */
	boolean completeAuthentication(String callbackUrl, DbxSessionStore csrfTokenStore, Map<String, String[]> request);

	/**
	 *	Changed return type to support dropbox java sdk
	 * @return fullAccount
	 */
	FullAccount getUserProfile();

	/**
	 *	Added to support dropbox java sdk
	 * @return spaceUsage
	 */
	SpaceUsage getUserSpaceUsage();

	/**
	 *	Changed return type to support dropbox java sdk
	 * @param path
	 * @return listFolderResult
	 */
	ListFolderResult getSpace(String path);

	/**
	 * Get the current users Dropbox metadata for the node from Dropbox
	 *
	 * @param nodeRef
	 * @return
	 */
	Metadata getMetadata(NodeRef nodeRef);

	/**
	 * Get the Dropbox metadata for the node from Alfresco for the current user.
	 * Lookup by nodes path.
	 *
	 * @param nodeRef
	 * @return
	 */
	Map<QName, Serializable> getPersistedMetadata(NodeRef nodeRef);

	/**
	 * Persist the Dropbox metadata to the node for the current user.
	 * If no other users have the node synced to dropbox the Dropbox aspect
	 * is added to the node.
	 *
	 * @param metadata
	 * @param nodeRef
	 */
	void persistMetadata(Metadata metadata, NodeRef nodeRef);

	/**
	 * Delete the persisted Dropbox Metadata from the Node for the current user.
	 * If no other users have the node synced, the Dropbox Aspect is also removed.
	 *
	 * @param nodeRef
	 * @return
	 */
	boolean deletePersistedMetadata(NodeRef nodeRef);

	/**
	 * Delete the persisted Dropbox Metadata from the Node for the named user.
	 * Can only be performed by an admin user or site manager.
	 * If no other users have the node synced, the Dropbox Aspect is also removed.
	 *
	 * @param nodeRef
	 * @param userAuthority
	 * @return
	 */
	boolean deletePersistedMetadata(final NodeRef nodeRef, String userAuthority);

	/**
	 * Retrieve the file from Drobox from the current users account.
	 * Lookup by path of the node. Writes file to the node.
	 *
	 * @param nodeRef
	 * @return
	 */
	Metadata getFile(NodeRef nodeRef);

	/**
	 * Send the node to the current users Dropbox.
	 * Location is set by path of current node.
	 *
	 * @param nodeRef
	 * @param overwrite Creates a new file. The new files name is name-<number>.extension
	 * @return
	 */
	Metadata putFile(NodeRef nodeRef, boolean overwrite);

	/**
	 * Create folder in the current users Dropbox account.
	 * Location is set by path of current node.
	 *
	 * @param nodeRef
	 * @return
	 */
	Metadata createFolder(NodeRef nodeRef);

	/**
	 * Get the Dropbox qualified path to the file for the node
	 *
	 * @param nodeRef
	 * @return
	 */
	String getDropboxPath(NodeRef nodeRef);

	/**
	 * Is the node synced to Dropbox for the current user.
	 *
	 * @param nodeRef
	 * @return
	 */
	boolean isSynced(NodeRef nodeRef);

	/**
	 * Return map of all users who currently have the node synced to their Dropbox accounts with nodeRef of persisted Metadata
	 *
	 * @param nodeRef
	 * @return
	 */
	Map<String, NodeRef> getSyncedUsers(NodeRef nodeRef);

	/**
	 * Move the file in the current users Dropbox.
	 *
	 * @param oldChildAssocRef from Child association reference for node
	 * @param newChildAssocRef to Child association reference for node
	 * @return
	 */
	Metadata move(ChildAssociationRef oldChildAssocRef, ChildAssociationRef newChildAssocRef);

	/**
	 * Copy the file in the current users Dropbox
	 *
	 * @param originalNodeRef   from the source node location
	 * @param newNodeRef        to the target node location
	 * @return
	 */
	Metadata copy(NodeRef originalNodeRef, NodeRef newNodeRef);

	/**
	 * Delete the node from the current users Dropbox
	 *
	 * @param nodeRef
	 * @return
	 */
	Metadata delete(NodeRef nodeRef);

	/**
	 * Delete the node from the current users Dropbox
	 *
	 * @param path
	 * @return
	 */
	Metadata delete(String path);

}
