/*
 * Copyright 2011-2012 Alfresco Software Limited.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 * 
 * This file is part of an unsupported extension to Alfresco.
 */

package org.alfresco.dropbox.service.policy;


import com.dropbox.core.v2.files.Metadata;
import com.fikatechnologies.dropbox.DropboxConnector;
import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.service.action.DropboxDeleteAction;
import org.alfresco.dropbox.service.action.DropboxMoveAction;
import org.alfresco.dropbox.service.action.DropboxUpdateAction;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentServicePolicies.OnContentUpdatePolicy;
import org.alfresco.repo.copy.CopyBehaviourCallback;
import org.alfresco.repo.copy.CopyDetails;
import org.alfresco.repo.copy.CopyServicePolicies;
import org.alfresco.repo.copy.CopyServicePolicies.OnCopyNodePolicy;
import org.alfresco.repo.copy.CopyServicePolicies.OnCopyCompletePolicy;
import org.alfresco.repo.node.NodeServicePolicies.BeforeDeleteNodePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnCreateChildAssociationPolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnMoveNodePolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.*;


/**
 * 
 * 
 * @author Jared Ottley
 */
public class DropboxAspect
    implements OnContentUpdatePolicy, OnCreateChildAssociationPolicy, BeforeDeleteNodePolicy, OnCopyNodePolicy, OnMoveNodePolicy, OnCopyCompletePolicy
{

    private static final Log    logger                   = LogFactory.getLog(DropboxAspect.class);

    private PolicyComponent     policyComponent;
    private NodeService         nodeService;
    private ActionService       actionService;
    private DropboxConnector    dropboxConnector;

    private static final String DROPBOX_UPDATE_ACTION = "dropboxUpdateAction";
    private static final String DROPBOX_DELETE_ACTION = "dropboxDeleteAction";
    private static final String DROPBOX_MOVE_ACTION   = "dropboxMoveAction";

    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }


    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setActionService(ActionService actionService)
    {
        this.actionService = actionService;
    }


    public void setDropboxConnector(DropboxConnector dropboxConnector)
    {
        this.dropboxConnector = dropboxConnector;
    }


    public void init()
    {
        policyComponent.bindClassBehaviour(OnContentUpdatePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "onContentUpdate", NotificationFrequency.TRANSACTION_COMMIT));
        policyComponent.bindAssociationBehaviour(OnCreateChildAssociationPolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, ContentModel.ASSOC_CONTAINS, new JavaBehaviour(this, "onCreateChildAssociation", NotificationFrequency.TRANSACTION_COMMIT));
        policyComponent.bindClassBehaviour(BeforeDeleteNodePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "beforeDeleteNode", NotificationFrequency.FIRST_EVENT));
        policyComponent.bindClassBehaviour(OnCopyNodePolicy.QNAME, DropboxConstants.Model.ASPECT_SYNCABLE, new JavaBehaviour(this, "getCopyCallback"));
        policyComponent.bindClassBehaviour(CopyServicePolicies.OnCopyCompletePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "onCopyComplete", NotificationFrequency.FIRST_EVENT));
        policyComponent.bindClassBehaviour(OnMoveNodePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "onMoveNode", NotificationFrequency.FIRST_EVENT));
    }

    //Added to handle copying of nodes for dropbox
    public CopyBehaviourCallback getCopyCallback(QName qName, CopyDetails copyDetails) {
        return new DropboxAspectCopyBehaviourCallback(DropboxConstants.Model.ASPECT_DROPBOX, DropboxConstants.Model.ASPECT_SYNCABLE);
    }

    //
    public void onCopyComplete(QName classRef, NodeRef sourceNodeRef, NodeRef targetNodeRef, boolean copyToNewNode, Map<NodeRef, NodeRef> copyMap) {
        logger.debug("In here now");
        if(!nodeService.hasAspect(targetNodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS)){
            nodeService.addAspect(targetNodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS, null);
        }

        //Adding this since a "Copy" action will not work
        Map<String, NodeRef> syncedUsers = dropboxConnector.getSyncedUsers(sourceNodeRef);

        for (final Map.Entry<String, NodeRef> syncedUser : syncedUsers.entrySet())
        {
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Metadata>()
            {
                public Metadata doWork()
                        throws Exception
                {
                    Metadata metadata = dropboxConnector.copy(sourceNodeRef, targetNodeRef);
                    dropboxConnector.persistMetadata(metadata, (targetNodeRef));

                    logger.debug("Dropbox: Copied from "
                            + sourceNodeRef.toString() + " to "
                            + targetNodeRef.toString());

                    if (nodeService.hasAspect(targetNodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
                    {
                        nodeService.removeAspect(targetNodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS);
                    }

                    return metadata;
                }
            }, syncedUser.getKey());
        }

    }//*/


    public void onContentUpdate(NodeRef nodeRef, boolean newContent)
    {
        if (!newContent)
        {
            if (!nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
            {
                nodeService.addAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS, null);
            }

            actionService.executeAction(actionService.createAction(DROPBOX_UPDATE_ACTION), nodeRef, false, true);

            logger.debug("Dropbox: Updating " + nodeRef.toString() + "in Dropbox");
        }
    }


    public void onCreateChildAssociation(ChildAssociationRef childAssocRef, boolean isNewNode)
    {
        if (!nodeService.hasAspect(childAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
        {
            nodeService.addAspect(childAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS, null);
        }

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put(DropboxUpdateAction.DROPBOX_USE_PARENT, true);

        actionService.executeAction(actionService.createAction(DROPBOX_UPDATE_ACTION, params), childAssocRef.getChildRef(), false, true);

        logger.debug("Dropbox: New child (" + childAssocRef.getChildRef().toString() + ") in Synced Folder"
                  + childAssocRef.getParentRef().toString() + " will be synced to Dropbox.");
    }


    public void beforeDeleteNode(NodeRef nodeRef)
    {
        if (nodeService.exists(nodeRef))
        {
            List<String> users = new ArrayList<String>();
            Map<String, Serializable> params = new HashMap<String, Serializable>();
            //Changed due to getDropboxPath method changes
            params.put(DropboxDeleteAction.DROPBOX_PATH, dropboxConnector.getDropboxPath(nodeRef) /*+ "/"
                                                         + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)*/);
            Map<String, NodeRef> syncedUsers = dropboxConnector.getSyncedUsers(nodeRef);
            users.addAll(syncedUsers.keySet());
            params.put(DropboxDeleteAction.DROPBOX_USERS, (Serializable)users);

            actionService.executeAction(actionService.createAction(DROPBOX_DELETE_ACTION, params), nodeRef, false, true);

            logger.debug("Dropbox: Deleting " + nodeRef.toString() + " from Dropbox.");
        }
    }

    public void onMoveNode(ChildAssociationRef oldChildAssocRef, ChildAssociationRef newChildAssocRef)
    {
        if (!nodeService.hasAspect(newChildAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
        {
            nodeService.addAspect(newChildAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS, null);
        }

        Map<String, Serializable> params = new HashMap<String, Serializable>();

        params.put(DropboxMoveAction.DROPBOX_FROM_PATH, oldChildAssocRef);
        params.put(DropboxMoveAction.DROPBOX_TO_PATH, newChildAssocRef);

        actionService.executeAction(actionService.createAction(DROPBOX_MOVE_ACTION, params), null, false, true);

        logger.debug("Dropbox: Moving " + newChildAssocRef.getChildRef().toString() + " and any children");
    }

}
