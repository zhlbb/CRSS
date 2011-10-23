package de.deepamehta.plugins.facets;

import de.deepamehta.plugins.facets.service.FacetsService;

import de.deepamehta.core.AssociationDefinition;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.CompositeValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.service.ClientContext;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.Plugin;

import java.util.logging.Logger;



public class FacetsPlugin extends Plugin implements FacetsService {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // **********************
    // *** Plugin Service ***
    // **********************



    @Override
    public void associateWithFacetType(long topicId, String facetTypeUri) {
        dms.createAssociation(new AssociationModel("dm4.core.instantiation", 
            new TopicRoleModel(topicId,      "dm4.core.instance"),
            new TopicRoleModel(facetTypeUri, "dm4.facets.facet")), null);   // clientContext=null
    }

    @Override
    public void addFacet(Topic topic, String facetTypeUri, TopicModel facet, ClientContext clientContext,
                                                                             Directives directives) {
        String assocDefUri = facet.getTypeUri();
        AssociationDefinition assocDef = dms.getTopicType(facetTypeUri, null).getAssocDef(assocDefUri);
        String childTopicTypeUri = assocDef.getPartTopicTypeUri();
        TopicType childTopicType = dms.getTopicType(childTopicTypeUri, null);
        String assocTypeUri = assocDef.getTypeUri();
        if (assocTypeUri.equals("dm4.core.composition_def")) {
            if (childTopicType.getDataTypeUri().equals("dm4.core.composite")) {
                Topic childTopic = fetchChildTopic(topic, assocDef);
                CompositeValue childTopicComp = facet.getCompositeValue();
                if (childTopic != null) {
                    TopicModel model = new TopicModel(childTopic.getId(), childTopicComp);
                    childTopic.update(model, clientContext, directives);
                } else {
                    // create and associate child topic
                    childTopic = dms.createTopic(new TopicModel(childTopicTypeUri, childTopicComp), null);
                    associateChildTopic(topic, assocDef, childTopic.getId());
                    // Note: the child topic must be created right with its composite value.
                    // Otherwise its label can't be calculated.
                }
            } else {
                throw new RuntimeException("Simple facets not yet supported");
                // ### setChildTopicValue(assocDefUri, new SimpleValue(value));
            }
        } else if (assocTypeUri.equals("dm4.core.aggregation_def")) {
            throw new RuntimeException("Facet aggregation not yet supported");
        } else {
            throw new RuntimeException("Association type \"" + assocTypeUri + "\" not supported");
        }
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    /**
     * Fetches and returns a child topic or <code>null</code> if no such topic extists.
     * <p>
     * Note: There is a principal copy in AttachedDeepaMehtaObject but here the precondition is different:
     * The given AssociationDefinition is not necessarily part of the given topic's type.
     */
    private Topic fetchChildTopic(Topic topic, AssociationDefinition assocDef) {
        String assocTypeUri       = assocDef.getInstanceLevelAssocTypeUri();
        String myRoleTypeUri      = assocDef.getWholeRoleTypeUri();
        String othersRoleTypeUri  = assocDef.getPartRoleTypeUri();
        String othersTopicTypeUri = assocDef.getPartTopicTypeUri();
        //
        return topic.getRelatedTopic(assocTypeUri, myRoleTypeUri, othersRoleTypeUri, othersTopicTypeUri, true, false);
        // fetchComposite=true ### FIXME: make fetchComposite a parameter
    }

    /**
     * Note: There is a principal copy in AttachedDeepaMehtaObject but here the precondition is different:
     * The given AssociationDefinition is not necessarily part of the given topic's type.
     */
    private void associateChildTopic(Topic topic, AssociationDefinition assocDef, long childTopicId) {
        dms.createAssociation(new AssociationModel(assocDef.getInstanceLevelAssocTypeUri(),
            new TopicRoleModel(topic.getId(), assocDef.getWholeRoleTypeUri()),
            new TopicRoleModel(childTopicId,  assocDef.getPartRoleTypeUri())), null);   // clientContext=null
    }
}
