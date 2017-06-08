package org.alfresco.dropbox.service.policy;

import org.alfresco.repo.copy.CompoundCopyBehaviourCallback;
import org.alfresco.repo.copy.CopyBehaviourCallback;
import org.alfresco.repo.copy.CopyDetails;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class DropboxAspectCopyBehaviourCallback extends CompoundCopyBehaviourCallback {
    private Log logger = LogFactory.getLog(DropboxAspectCopyBehaviourCallback.class);

    private QName classQName;
    private QName additionalQName;
    private List<CopyBehaviourCallback> callbacks;

    DropboxAspectCopyBehaviourCallback(QName classQName, QName additionalQName) {
        super(classQName);
        this.classQName = classQName;
        this.additionalQName = additionalQName;
        callbacks = new ArrayList<CopyBehaviourCallback>();
    }

    public void addBehaviour(CopyBehaviourCallback callback)
    {
        callbacks.add(callback);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("\n")
                .append("CompoundCopyBehaviourCallback: \n")
                .append("      Model Class: ").append(classQName)
                .append("\n      Second Class: ").append(additionalQName);
        boolean first = true;
        for (CopyBehaviourCallback callback : callbacks)
        {
            if (first)
            {
                first = false;
                sb.append("\n");
            }
            sb.append("      ").append(callback.getClass().getName());
        }
        return sb.toString();
    }

    @Override
    public Pair<AssocCopySourceAction, AssocCopyTargetAction> getAssociationCopyAction(QName classQName,
                                                                                       CopyDetails copyDetails,
                                                                                       CopyAssociationDetails assocCopyDetails) {
        return getAssociationCopyAction(classQName, this.additionalQName, copyDetails, assocCopyDetails);
    }

    public Pair<AssocCopySourceAction, AssocCopyTargetAction> getAssociationCopyAction(
            QName classQName,
            QName additionalQName,
            CopyDetails copyDetails,
            CopyAssociationDetails assocCopyDetails)
    {
        AssocCopySourceAction bestSourceAction = AssocCopySourceAction.COPY;
        AssocCopyTargetAction bestTargetAction = AssocCopyTargetAction.USE_ORIGINAL_TARGET;
        for (CopyBehaviourCallback callback : callbacks)
        {
            Pair<AssocCopySourceAction, AssocCopyTargetAction> action = callback.getAssociationCopyAction(
                    classQName,
                    copyDetails,
                    assocCopyDetails);
            if (action.getFirst().compareTo(bestSourceAction) > 0)
            {
                // We've trumped the last best one
                bestSourceAction = action.getFirst();
            }
            if (action.getSecond().compareTo(bestTargetAction) > 0)
            {
                // We've trumped the last best one
                bestTargetAction = action.getSecond();
            }
        }
        Pair<AssocCopySourceAction, AssocCopyTargetAction> bestAction =
                new Pair<AssocCopySourceAction, AssocCopyTargetAction>(bestSourceAction, bestTargetAction);
        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(
                    "Association copy behaviour: " + bestAction + "\n" +
                            "   " + assocCopyDetails + "\n" +
                            "   " + copyDetails + "\n" +
                            "   " + this);
        }
        return bestAction;
    }

    /**
     * Individual callbacks effectively have a veto on the copy i.e. if one of the
     * callbacks returns <tt>false</tt> for {@link ,
     * then the copy will NOT proceed.  However, a warning is generated indicating that
     * there is a conflict in the defined behaviour.
     *
     * @return          Returns <tt>true</tt> if all registered callbacks return <tt>true</tt>
     *                  or <b><tt>false</tt> if any of the  registered callbacks return <tt>false</tt></b>.
     */
    @Override
    public boolean getMustCopy(QName classQName, CopyDetails copyDetails)
    {
        return getMustCopy(classQName, this.additionalQName, copyDetails);
    }

    /**
     * Individual callbacks effectively have a veto on the copy i.e. if one of the
     * callbacks returns <tt>false</tt> for ,
     * then the copy will NOT proceed.  However, a warning is generated indicating that
     * there is a conflict in the defined behaviour.
     *
     * @return          Returns <tt>true</tt> if all registered callbacks return <tt>true</tt>
     *                  or <b><tt>false</tt> if any of the  registered callbacks return <tt>false</tt></b>.
     */
    private boolean getMustCopy(QName classQName, QName additionalQName, CopyDetails copyDetails)
    {
        CopyBehaviourCallback firstVeto = null;
        for (CopyBehaviourCallback callback : callbacks)
        {
            boolean mustCopyLocal = callback.getMustCopy(classQName, copyDetails);
            if (firstVeto == null && !mustCopyLocal)
            {
                firstVeto = callback;
            }
            if (mustCopyLocal && firstVeto != null)
            {
                // The callback says 'copy' but there is already a veto in place
                logger.warn(
                        "CopyBehaviourCallback '" + callback.getClass().getName() + "' " +
                                "is attempting to induce a copy when callback '" + firstVeto.getClass().getName() +
                                "' has already vetoed it.  Copying of '" + copyDetails.getSourceNodeRef() +
                                "' will not occur.");
            }
            boolean mustLocalCopySecondPass = callback.getMustCopy(additionalQName, copyDetails);
            if(firstVeto == null && !mustLocalCopySecondPass)
            {
                firstVeto = callback;
            }
            if(mustLocalCopySecondPass && firstVeto != null)
            {
                // The callback says 'copy' but there is already a veto in place
                logger.warn(
                        "CopyBehaviourCallback '" + callback.getClass().getName() + "' " +
                                "is attempting to induce a copy when callback '" + firstVeto.getClass().getName() +
                                "' has already vetoed it.  Copying of '" + copyDetails.getSourceNodeRef() +
                                "' will not occur.");
            }
        }
        // Done
        if (firstVeto == null)
        {
            // Allowed by all
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "All copy behaviours voted for a copy of node \n" +
                                "   " + copyDetails + "\n" +
                                "   " + this);
            }
            return true;
        }
        else
        {
            // Vetoed
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "Copy behaviour vetoed for node " + copyDetails.getSourceNodeRef() + "\n" +
                                "   First veto: " + firstVeto.getClass().getName() + "\n" +
                                "   " + copyDetails + "\n" +
                                "   " + this);
            }
            return false;
        }
    }

    /**
     * Uses the {@link ChildAssocCopyAction} ordering to drive priority i.e. a vote
     * to copy will override a vote NOT to copy.
     *
     * @return          Returns the most lively choice of action
     */
    @Override
    public ChildAssocCopyAction getChildAssociationCopyAction(
            QName classQName,
            CopyDetails copyDetails,
            CopyChildAssociationDetails childAssocCopyDetails)
    {
        return getChildAssociationCopyAction(classQName, this.additionalQName, copyDetails, childAssocCopyDetails);
    }

    /**
     * Uses the {@link ChildAssocCopyAction} ordering to drive priority i.e. a vote
     * to copy will override a vote NOT to copy.
     *
     * @return          Returns the most lively choice of action
     */
    private ChildAssocCopyAction getChildAssociationCopyAction(
            QName classQName,
            QName additionalQName,
            CopyDetails copyDetails,
            CopyChildAssociationDetails childAssocCopyDetails)
    {
        ChildAssocCopyAction bestAction = ChildAssocCopyAction.COPY_CHILD;
        for (CopyBehaviourCallback callback : callbacks)
        {
            ChildAssocCopyAction action = callback.getChildAssociationCopyAction(
                    classQName,
                    copyDetails,
                    childAssocCopyDetails);
            if (action.compareTo(bestAction) > 0)
            {
                // We've trumped the last best one
                bestAction = action;
            }
            ChildAssocCopyAction action2 = callback.getChildAssociationCopyAction(additionalQName, copyDetails, childAssocCopyDetails);
            if(action2.compareTo(bestAction) > 0){
                bestAction = action2;
            }
        }
        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(
                    "Child association copy behaviour: " + bestAction + "\n" +
                            "   " + childAssocCopyDetails + "\n" +
                            "   " + copyDetails + "\n" +
                            "   " + this);
        }
        return bestAction;
    }

    /**
     * Uses the {@link ChildAssocRecurseAction} ordering to drive recursive copy behaviour.
     *
     * @return          Returns the most lively choice of action
     */
    @Override
    public ChildAssocRecurseAction getChildAssociationRecurseAction(
            QName classQName,
            CopyDetails copyDetails,
            CopyChildAssociationDetails childAssocCopyDetails)
    {
        return getChildAssociationRecurseAction(classQName, this.additionalQName, copyDetails, childAssocCopyDetails);
    }

    /**
     * Uses the {@link ChildAssocRecurseAction} ordering to drive recursive copy behaviour.
     *
     * @return          Returns the most lively choice of action
     */
    private ChildAssocRecurseAction getChildAssociationRecurseAction(
            QName classQName,
            QName additionalQName,
            CopyDetails copyDetails,
            CopyChildAssociationDetails childAssocCopyDetails)
    {
        ChildAssocRecurseAction bestAction = ChildAssocRecurseAction.RESPECT_RECURSE_FLAG;
        for (CopyBehaviourCallback callback : callbacks)
        {
            ChildAssocRecurseAction action = callback.getChildAssociationRecurseAction(
                    classQName,
                    copyDetails,
                    childAssocCopyDetails);
            if (action.compareTo(bestAction) > 0)
            {
                // We've trumped the last best one
                bestAction = action;
            }
            ChildAssocRecurseAction action2 = callback.getChildAssociationRecurseAction(additionalQName, copyDetails, childAssocCopyDetails);
            if(action2.compareTo(bestAction) > 0){
                bestAction = action2;
            }
        }
        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(
                    "Child association recursion behaviour: " + bestAction + "\n" +
                            "   " + childAssocCopyDetails + "\n" +
                            "   " + copyDetails + "\n" +
                            "   " + this);
        }
        return bestAction;
    }

    /**
     * The lowest common denominator applies here.  The properties are passed to each
     * callback in turn.  The resulting map is then passed to the next callback and so
     * on.  If any callback removes or alters properties, these will not be recoverable.
     *
     * @return          Returns the least properties assigned for the copy by any individual
     *                  callback handler
     */
    @Override
    public Map<QName, Serializable> getCopyProperties(
            QName classQName,
            CopyDetails copyDetails,
            Map<QName, Serializable> properties)
    {
        return getCopyProperties(classQName, this.additionalQName, copyDetails, properties);
    }

    /**
     * The lowest common denominator applies here.  The properties are passed to each
     * callback in turn.  The resulting map is then passed to the next callback and so
     * on.  If any callback removes or alters properties, these will not be recoverable.
     *
     * @return          Returns the least properties assigned for the copy by any individual
     *                  callback handler
     */
    private Map<QName, Serializable> getCopyProperties(
            QName classQName,
            QName additionalQName,
            CopyDetails copyDetails,
            Map<QName, Serializable> properties)
    {
        Map<QName, Serializable> copyProperties = new HashMap<QName, Serializable>(properties);
        Map<QName, Serializable> moreProps = new HashMap<>();
        for (CopyBehaviourCallback callback : callbacks)
        {
            Map<QName, Serializable> propsToCopy = callback.getCopyProperties(classQName,
                    copyDetails,
                    copyProperties);

            Map<QName, Serializable> otherPropsToCopy = callback.getCopyProperties(additionalQName, copyDetails, copyProperties);

            if(propsToCopy != copyProperties)
            {
                /*
                 * Collections.emptyMap() is a valid return from the callback so we need to ensure it
                 * is still mutable for the next iteration
                 */
                copyProperties = new HashMap<QName, Serializable>(propsToCopy);
            }
            moreProps = otherPropsToCopy;
        }
        copyProperties.putAll(moreProps);
        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(
                    "Copy properties: \n" +
                            "   " + copyDetails + "\n" +
                            "   " + this + "\n" +
                            "   Before: " + properties + "\n" +
                            "   After:  " + copyProperties);
        }
        return copyProperties;
    }
}
