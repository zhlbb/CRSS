package systems.dmx.core.impl;

import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.CompDefModel;
import systems.dmx.core.model.DMXObjectModel;

import java.util.List;
import java.util.logging.Logger;



class ChildTopicsFetcher {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private AccessLayer al;

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    ChildTopicsFetcher(AccessLayer al) {
        this.al = al;
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    /**
     * Fetches the child topic models (recursively) of the given object model and updates it in-place.
     * ### TODO: recursion is required in some cases (e.g. when fetching a topic through REST API) but is possibly
     * overhead in others (e.g. when updating composite structures).
     * <p>
     * Works for both, "one" and "many" comp defs.
     *
     * @param   compDef     The child topic models according to this comp def are fetched.
     */
    void fetch(DMXObjectModel object, CompDefModel compDef, boolean deep) {
        try {
            ChildTopicsModel childTopics = object.getChildTopicsModel();
            String cardinalityUri = compDef.getChildCardinalityUri();
            String compDefUri     = compDef.getCompDefUri();
            if (cardinalityUri.equals("dmx.core.one")) {
                RelatedTopicModelImpl childTopic = fetchChildTopic(object.getId(), compDef);
                // Note: topics just created have no child topics yet
                if (childTopic != null) {
                    childTopics.put(compDefUri, childTopic);
                    if (deep) {
                        fetchChildTopics(childTopic, deep);    // recursion
                    }
                }
            } else if (cardinalityUri.equals("dmx.core.many")) {
                for (RelatedTopicModelImpl childTopic : fetchChildTopics(object.getId(), compDef)) {
                    childTopics.add(compDefUri, childTopic);
                    if (deep) {
                        fetchChildTopics(childTopic, deep);    // recursion
                    }
                }
            } else {
                throw new RuntimeException("\"" + cardinalityUri + "\" is an unexpected cardinality URI");
            }
        } catch (Exception e) {
            throw new RuntimeException("Fetching the \"" + compDef.getCompDefUri() + "\" child topics of object " +
                object.getId() + " failed", e);
        }
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    /**
     * Fetches the child topic models (recursively) of the given object model and updates it in-place.
     * ### TODO: recursion is required in some cases (e.g. when fetching a topic through REST API) but is possibly
     * overhead in others (e.g. when updating composite structures).
     */
    private void fetchChildTopics(DMXObjectModelImpl object, boolean deep) {
        for (CompDefModel compDef : object.getType().getCompDefs()) {
            fetch(object, compDef, deep);
        }
    }

    // ---

    /**
     * Fetches and returns a child topic or <code>null</code> if no such topic extists.
     */
    private RelatedTopicModelImpl fetchChildTopic(long objectId, CompDefModel compDef) {
        return al.sd.fetchRelatedTopic(
            objectId,
            compDef.getInstanceLevelAssocTypeUri(),
            "dmx.core.parent", "dmx.core.child",
            compDef.getChildTypeUri()
        );
    }

    private List<RelatedTopicModelImpl> fetchChildTopics(long objectId, CompDefModel compDef) {
        return al.db.fetchRelatedTopics(
            objectId,
            compDef.getInstanceLevelAssocTypeUri(),
            "dmx.core.parent", "dmx.core.child",
            compDef.getChildTypeUri()
        );
    }
}
