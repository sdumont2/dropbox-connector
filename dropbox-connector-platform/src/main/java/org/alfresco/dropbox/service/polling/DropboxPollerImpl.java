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

package org.alfresco.dropbox.service.polling;


import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.fikatechnologies.dropbox.DropboxConnector;
import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.exceptions.NotModifiedException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.search.impl.lucene.LuceneQueryParserException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;


/**
 *  Changes here to updated handling based on new api/sdk and metadata class for dropbox
 *  connector and adding the ability to execute only
 *  based on whether or not a boolean property is set to enabled polling
 * 
 * @author Jared Ottley
 */
public class DropboxPollerImpl
    implements DropboxPoller
{
    private static final Log     log                          = LogFactory.getLog(DropboxPollerImpl.class);

    private SearchService        searchService;
    private NodeService          nodeService;
    private FileFolderService    fileFolderService;
    private TransactionService   transactionService;
    private DropboxConnector     dropboxConnector;

    private boolean isPollingEnabled;

    private static final String  CMIS_DROPBOX_SITES_QUERY     = "SELECT * FROM st:site AS S JOIN db:syncable AS O ON S.cmis:objectId = O.cmis:objectId";
    private static final String  CMIS_DROPBOX_DOCUMENTS_QUERY = "SELECT D.* FROM cmis:document AS D JOIN db:dropbox AS O ON D.cmis:objectId = O.cmis:objectId";
    private static final String  CMIS_DROPBOX_FOLDERS_QUERY   = "SELECT F.* FROM cmis:folder AS F JOIN db:dropbox AS O ON F.cmis:objectId = O.cmis:objectId";

    private static final NodeRef MISSING_NODE                 = new NodeRef("missing://missing/missing");


    public void setSearchService(SearchService searchService)
    {
        this.searchService = searchService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    public void setIsPollingEnabled(boolean isPollingEnabled){
        this.isPollingEnabled = isPollingEnabled;
    }

    public void setDropboxConnector(DropboxConnector dropboxConnector)
    {
        this.dropboxConnector = dropboxConnector;
    }


    public void execute()
    {
        if(isPollingEnabled) {
            log.debug("Dropbox poller initiated.");

            // TODO where should the authentication and transactions go?
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>() {

                public Object doWork()
                        throws Exception {
                    RetryingTransactionCallback<Object> txnWork = new RetryingTransactionCallback<Object>() {
                        public Object execute()
                                throws Exception {
                            List<NodeRef> sites = getSites();

                            List<NodeRef> folders = null;
                            List<NodeRef> documents = null;

                            if (sites != null) {
                                for (NodeRef site : sites) {
                                    if (!isSyncing(site)) {
                                        log.debug("Processing Content in " + nodeService.getProperty(site, ContentModel.PROP_NAME));
                                        try {
                                            syncOn(site);

                                            folders = getSiteFolders(site);
                                            documents = getSiteDocuments(site);


                                            if (documents != null) {
                                                // If the document is the child of a synced folder...we want to work on the folder as a
                                                // full collection and not the document as an independent element
                                                Iterator<NodeRef> i = documents.iterator();

                                                while (i.hasNext()) {
                                                    NodeRef document = i.next();
                                                    if (folders.contains(nodeService.getPrimaryParent(document).getParentRef())) {
                                                        i.remove();
                                                    }
                                                }
                                                if (documents.size() > 0) {
                                                    for (NodeRef document : documents) {
                                                        updateNode(document);
                                                    }
                                                }
                                            }

                                            if (folders.size() > 0) {
                                                for (NodeRef folder : folders) {
                                                    log.debug("Looking for updates/new content in "
                                                            + nodeService.getProperty(folder, ContentModel.PROP_NAME));

                                                    try {
                                                        Metadata metadata = dropboxConnector.getMetadata(folder);

                                                        // Get the list of the content returned.
                                                        // Changes for new API
                                                        //List<Metadata> list = metadata.getContents();
                                                        List<Metadata> list = dropboxConnector.getSpace(dropboxConnector.getDropboxPath(folder)).getEntries();

                                                        for (Metadata child : list) {
                                                            //TODO Make a reverse method from the getDropboxPath, and make a getAlfrescoPath method.
                                                            //TODO See if this works as is first
                                                            String name = child.getPathDisplay().replaceAll(Matcher.quoteReplacement(metadata.getPathDisplay()
                                                                    + "/"), "");

                                                            NodeRef childNodeRef = fileFolderService.searchSimple(folder, name);

                                                            if (childNodeRef == null) {
                                                                addNode(folder, child, name);
                                                            } else {
                                                                updateNode(childNodeRef, child);
                                                            }
                                                        }

                                                        metadata = dropboxConnector.getMetadata(folder);

                                                        dropboxConnector.persistMetadata(metadata, folder);
                                                    } catch (NotModifiedException nme) {
                                                        // TODO
                                                    }
                                                }
                                            }
                                        } finally {
                                            syncOff(site);
                                            log.debug("End processing " + nodeService.getProperty(site, ContentModel.PROP_NAME));

                                            documents = null;
                                            folders = null;
                                        }
                                    }

                                }
                            }

                            folders = getFolders();
                            documents = getDocuments();

                            if (documents != null) {
                                // If the document is the child of a synced folder...we want to work on the folder as a
                                // full collection and not the document as an independent element
                                Iterator<NodeRef> i = documents.iterator();

                                while (i.hasNext()) {
                                    NodeRef document = i.next();
                                    if (folders.size() > 0) {
                                        if (folders.contains(nodeService.getPrimaryParent(document).getParentRef()) && nodeService.hasAspect(nodeService.getPrimaryParent(document).getParentRef(), DropboxConstants.Model.ASPECT_SYNCABLE)) {
                                            i.remove();
                                        }
                                    }
                                }
                                if (documents.size() > 0) {
                                    for (NodeRef document : documents) {
                                        if (!isSyncing(document)) {
                                            syncOn(document);
                                            updateNode(document);
                                            syncOff(document);
                                        }
                                    }
                                }
                            }


                            if (folders.size() > 0) {
                                for (NodeRef folder : folders) {
                                    log.debug("Looking for updates/new content in "
                                            + nodeService.getProperty(folder, ContentModel.PROP_NAME));
                                    if (!isSyncing(folder)) {
                                        syncOn(folder);
                                        try {
                                            Metadata metadata = dropboxConnector.getMetadata(folder);

                                            // Get the list of the content returned.
                                            // Changes for new API here
                                            //List<Metadata> list = metadata.getContents();

                                            List<Metadata> list = dropboxConnector.getSpace(dropboxConnector.getDropboxPath(folder)).getEntries();

                                            for (Metadata child : list) {
                                                //TODO Make a reverse method from the getDropboxPath, and make a getAlfrescoPath method.
                                                //TODO See if this works as is first
                                                String name = child.getPathDisplay().replaceAll(Matcher.quoteReplacement(metadata.getPathDisplay()
                                                        + "/"), "");

                                                NodeRef childNodeRef = fileFolderService.searchSimple(folder, name);

                                                if (childNodeRef == null) {
                                                    addNode(folder, child, name);
                                                } else {
                                                    updateNode(childNodeRef, child);
                                                }
                                            }

                                            metadata = dropboxConnector.getMetadata(folder);

                                            dropboxConnector.persistMetadata(metadata, folder);
                                        } catch (NotModifiedException nme) {
                                            // TODO
                                        }
                                        syncOff(folder);
                                    }
                                }
                            }

                            return null;
                        }
                    };

                    transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                    return null;
                }
            }, AuthenticationUtil.getAdminUserName());
        }else{
            if(log.isTraceEnabled()) {
                log.trace("Polling not enabled. Doing nothing.");
            }
        }

    }


    private List<NodeRef> getSites()
    {
        List<NodeRef> sites = new ArrayList<NodeRef>();

        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {
                ResultSet resultSet = null;
                try
                {
                    resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_SITES_QUERY);

                }
                catch (LuceneQueryParserException lqpe)
                {
                    // This is primarily to handle the case where the dropbox model has not been added to solr yet. Incidentally it
                    // catches other failures too ;)
                    log.info("Unable to perform site query: " + lqpe.getMessage());
                }

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        // TODO Hopefully one day this will go away --Open Bug??

        try
        {
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    sites = resultSet.getNodeRefs();
                    log.debug("Sites with Dropbox content: " + sites);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return sites;
    }


    private List<NodeRef> getSiteDocuments(final NodeRef nodeRef)
    {
        List<NodeRef> documents = Collections.synchronizedList(new ArrayList<NodeRef>());


        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {
                ResultSet resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_DOCUMENTS_QUERY
                                                                                                                                          + " WHERE IN_TREE(D, '"
                                                                                                                                          + nodeRef
                                                                                                                                          + "')");

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        try
        {
            // TODO Hopefully one day this will go away --Open Bug??
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    documents = resultSet.getNodeRefs();
                    log.debug("Documents synced to Dropbox: " + documents);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return documents;
    }

    private List<NodeRef> getSiteFolders(final NodeRef nodeRef)
    {
        List<NodeRef> folders = Collections.synchronizedList(new ArrayList<NodeRef>());

        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {
                //CHANGE this to only use the base query to just get all the folders

                ResultSet resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_FOLDERS_QUERY
                                                                                                                                          + " WHERE IN_TREE(F, '"
                                                                                                                                          + nodeRef
                                                                                                                                          + "')");

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        try
        {
            // TODO Hopefully one day this will go away --Open Bug??
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    folders = resultSet.getNodeRefs();
                    log.debug("Folders synced to Dropbox: " + folders);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return folders;
    }
    
    private List<NodeRef> getDocuments()
    {
        List<NodeRef> documents = Collections.synchronizedList(new ArrayList<NodeRef>());


        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {

                ResultSet resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_DOCUMENTS_QUERY);

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        try
        {
            // TODO Hopefully one day this will go away --Open Bug??
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    documents = resultSet.getNodeRefs();
                    log.debug("Documents synced to Dropbox: " + documents);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return documents;
    }

    private List<NodeRef> getFolders()
    {
        List<NodeRef> folders = Collections.synchronizedList(new ArrayList<NodeRef>());

        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {

                ResultSet resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_FOLDERS_QUERY);

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        try
        {
            // TODO Hopefully one day this will go away --Open Bug??
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    folders = resultSet.getNodeRefs();
                    log.debug("Folders synced to Dropbox: " + folders);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return folders;
    }


    private void updateNode(final NodeRef nodeRef)
    {
        Metadata metadata = dropboxConnector.getMetadata(nodeRef);

        updateNode(nodeRef, metadata);
    }


    private void updateNode(final NodeRef nodeRef, final Metadata metadata)
    {
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
        {
            public Object doWork()
                throws Exception
            {

                RetryingTransactionCallback<Object> txnWork = new RetryingTransactionCallback<Object>()
                {
                    public Object execute()
                        throws Exception
                    {

                        if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
                        {
                            try
                            {
                                Metadata metadata = dropboxConnector.getMetadata(nodeRef);

                                // Get the list of the content returned.
                                // Changes for new API
                                //List<Metadata> list = metadata.getContents();
                                List<Metadata> list = dropboxConnector.getSpace(dropboxConnector.getDropboxPath(nodeRef)).getEntries();

                                for (Metadata child : list)
                                {
                                    //TODO Make a reverse method from the getDropboxPath, and make a getAlfrescoPath method.
                                    //TODO See if this works as is first
                                    String name = child.getPathDisplay().replaceAll(Matcher.quoteReplacement(metadata.getPathDisplay()
                                            + "/"), "");

                                    NodeRef childNodeRef = fileFolderService.searchSimple(nodeRef, name);

                                    if (childNodeRef == null)
                                    {
                                        addNode(nodeRef, child, name);
                                    }
                                    else
                                    {
                                        updateNode(childNodeRef, child);
                                    }
                                }

                                metadata = dropboxConnector.getMetadata(nodeRef);

                                dropboxConnector.persistMetadata(metadata, nodeRef);
                            }
                            catch (NotModifiedException nme)
                            {

                            }

                        }
                        else
                        {
                            Serializable rev = nodeService.getProperty(nodeRef, DropboxConstants.Model.PROP_REV);
                            FileMetadata fileMetadata = (FileMetadata) metadata;
                            if (!fileMetadata.getRev().equals(rev))
                            {
                                Metadata metadata = null;
                                try
                                {
                                    metadata = dropboxConnector.getFile(nodeRef);
                                }
                                catch (ContentIOException cio)
                                {
                                    cio.printStackTrace();
                                }

                                if (metadata != null)
                                {
                                    dropboxConnector.persistMetadata(metadata, nodeRef);
                                }
                                else
                                {
                                    throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Dropbox metadata maybe out of sync for "
                                                                                            + nodeRef);
                                }
                            }


                        }
                        return null;
                    }
                };

                transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                return null;

            }
        }, AuthenticationUtil.getAdminUserName());
    }


    private void addNode(final NodeRef parentNodeRef, final Metadata metadata, final String name)
    {
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
        {

            public Object doWork()
                throws Exception
            {
                NodeRef nodeRef = null;

                if (metadata instanceof FolderMetadata)
                {
                    RetryingTransactionCallback<NodeRef> txnWork = new RetryingTransactionCallback<NodeRef>()
                    {
                        public NodeRef execute()
                            throws Exception
                        {
                            NodeRef nodeRef = null;
                            nodeRef = fileFolderService.create(parentNodeRef, name, ContentModel.TYPE_FOLDER).getNodeRef();

                            Metadata metadata = dropboxConnector.getMetadata(nodeRef);

                            // Get the list of the content returned.
                            // Changes for new API
                            //List<Metadata> list = metadata.getContents();
                            List<Metadata> list = dropboxConnector.getSpace(dropboxConnector.getDropboxPath(nodeRef)).getEntries();

                            for (Metadata child : list)
                            {
                                //TODO Make a reverse method from the getDropboxPath, and make a getAlfrescoPath method.
                                //TODO See if this works as is first
                                String name = child.getPathDisplay().replaceAll(Matcher.quoteReplacement(metadata.getPathDisplay()
                                        + "/"), "");

                                addNode(nodeRef, child, name);
                            }

                            return nodeRef;
                        }
                    };

                    nodeRef = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);
                }
                else
                {
                    log.debug("Adding " + metadata.getPathDisplay() + " to Alfresco");

                    RetryingTransactionCallback<NodeRef> txnWork = new RetryingTransactionCallback<NodeRef>()
                    {
                        public NodeRef execute()
                            throws Exception
                        {
                            NodeRef nodeRef = null;

                            try
                            {
                                nodeRef = fileFolderService.create(parentNodeRef, name, ContentModel.TYPE_CONTENT).getNodeRef();
                                Metadata metadata = dropboxConnector.getFile(nodeRef);

                                dropboxConnector.persistMetadata(metadata, parentNodeRef);
                            }
                            catch (ContentIOException cio)
                            {
                                cio.printStackTrace();
                            }

                            return nodeRef;
                        }
                    };

                    nodeRef = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);
                }
                dropboxConnector.persistMetadata(metadata, nodeRef);
                return null;

            }
        }, AuthenticationUtil.getAdminUserName());
    }


    private boolean isSyncing(final NodeRef nodeRef)
    {
        Boolean syncing = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>()
        {
            public Boolean doWork()
                throws Exception
            {
                RetryingTransactionCallback<Boolean> txnWork = new RetryingTransactionCallback<Boolean>()
                {
                    public Boolean execute()
                        throws Exception
                    {
                        boolean syncing = false;

                        if (nodeRef != null)
                        {
                            List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_SYNC_DETAILS, DropboxConstants.Model.DROPBOX);

                            if (childAssoc.size() == 1)
                            {
                                syncing = Boolean.valueOf(nodeService.getProperty(childAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_SYNCING).toString());
                            }
                        }

                        return syncing;
                    }
                };

                boolean syncing = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                return syncing;
            }
        }, AuthenticationUtil.getAdminUserName());

        return syncing;
    }


    private void syncOn(NodeRef nodeRef)
    {
        sync(nodeRef, true);
    }


    private void syncOff(NodeRef nodeRef)
    {
        sync(nodeRef, false);
    }


    private void sync(final NodeRef nodeRef, final boolean sync)
    {
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
        {
            public Object doWork()
                throws Exception
            {
                RetryingTransactionCallback<Object> txnWork = new RetryingTransactionCallback<Object>()
                {
                    public Object execute()
                        throws Exception
                    {

                        if (nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNCABLE))
                        {
                            List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_SYNC_DETAILS, DropboxConstants.Model.DROPBOX);

                            if (childAssoc.size() == 1)
                            {
                                nodeService.setProperty(childAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_SYNCING, sync);
                            }
                        }

                        return null;
                    }
                };

                transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                return null;
            }
        }, AuthenticationUtil.getAdminUserName());
    }


}
