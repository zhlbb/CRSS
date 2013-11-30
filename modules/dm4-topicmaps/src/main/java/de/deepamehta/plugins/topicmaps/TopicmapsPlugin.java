package de.deepamehta.plugins.topicmaps;

import de.deepamehta.plugins.topicmaps.model.TopicmapViewmodel;
import de.deepamehta.plugins.topicmaps.service.TopicmapsService;

import de.deepamehta.core.Association;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.AssociationRoleModel;
import de.deepamehta.core.model.CompositeValueModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



@Path("/topicmap")
@Consumes("application/json")
@Produces("application/json")
public class TopicmapsPlugin extends PluginActivator implements TopicmapsService {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DEFAULT_TOPICMAP_NAME     = "untitled";
    private static final String DEFAULT_TOPICMAP_URI      = "dm4.topicmaps.default_topicmap";
    private static final String DEFAULT_TOPICMAP_RENDERER = "dm4.webclient.default_topicmap_renderer";

    // association type semantics ### TODO: to be dropped. Model-driven manipulators required.
    private static final String TOPIC_MAPCONTEXT       = "dm4.topicmaps.topic_mapcontext";
    private static final String ASSOCIATION_MAPCONTEXT = "dm4.topicmaps.association_mapcontext";
    private static final String ROLE_TYPE_TOPICMAP     = "dm4.core.default";
    private static final String ROLE_TYPE_TOPIC        = "dm4.topicmaps.topicmap_topic";
    private static final String ROLE_TYPE_ASSOCIATION  = "dm4.topicmaps.topicmap_association";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Map<String, TopicmapRenderer> topicmapRenderers = new HashMap();
    private List<ViewmodelCustomizer> viewmodelCustomizers = new ArrayList();

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    public TopicmapsPlugin() {
        // Note: registering the default renderer in the InitializePluginListener would be too late.
        // The renderer is already needed in the PostInstallPluginListener.
        registerTopicmapRenderer(new DefaultTopicmapRenderer());
    }



    // ***************************************
    // *** TopicmapsService Implementation ***
    // ***************************************



    @GET
    @Path("/{id}")
    @Override
    public TopicmapViewmodel getTopicmap(@PathParam("id") long topicmapId) {
        try {
            return new TopicmapViewmodel(topicmapId, dms, viewmodelCustomizers);
        } catch (Exception e) {
            throw new RuntimeException("Fetching topicmap " + topicmapId + " failed", e);
        }
    }

    // ---

    @POST
    @Path("/{name}/{topicmap_renderer_uri}")
    @Override
    public Topic createTopicmap(@PathParam("name") String name,
                                @PathParam("topicmap_renderer_uri") String topicmapRendererUri,
                                @HeaderParam("Cookie") ClientState clientState) {
        return createTopicmap(name, null, topicmapRendererUri, clientState);
    }

    @Override
    public Topic createTopicmap(String name, String uri, String topicmapRendererUri, ClientState clientState) {
        CompositeValueModel topicmapState = getTopicmapRenderer(topicmapRendererUri).initialTopicmapState();
        return dms.createTopic(new TopicModel(uri, "dm4.topicmaps.topicmap", new CompositeValueModel()
            .put("dm4.topicmaps.name", name)
            .put("dm4.topicmaps.topicmap_renderer_uri", topicmapRendererUri)
            .put("dm4.topicmaps.state", topicmapState)), clientState);
    }

    // ---

    @POST
    @Path("/{id}/topic/{topic_id}")
    @Override
    public void addTopicToTopicmap(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                   CompositeValueModel viewProps) {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            dms.createAssociation(new AssociationModel(TOPIC_MAPCONTEXT,
                new TopicRoleModel(topicmapId, ROLE_TYPE_TOPICMAP),
                new TopicRoleModel(topicId,    ROLE_TYPE_TOPIC), viewProps), null); // FIXME: clientState=null
            storeCustomViewProperties(topicmapId, topicId, viewProps);
            //
            tx.success();
        } catch (Exception e) {
            throw new RuntimeException("Adding topic " + topicId + " to topicmap " + topicmapId + " failed " +
                "(viewProps=" + viewProps + ")", e);
        } finally {
            tx.finish();
        }
    }

    @Override
    public void addTopicToTopicmap(long topicmapId, long topicId, int x, int y, boolean visibility) {
        addTopicToTopicmap(topicmapId, topicId, new StandardViewProperties(x, y, visibility));
    }

    @POST
    @Path("/{id}/association/{assoc_id}")
    @Override
    public void addAssociationToTopicmap(@PathParam("id") long topicmapId, @PathParam("assoc_id") long assocId) {
        dms.createAssociation(new AssociationModel(ASSOCIATION_MAPCONTEXT,
            new TopicRoleModel(topicmapId,    ROLE_TYPE_TOPICMAP),
            new AssociationRoleModel(assocId, ROLE_TYPE_ASSOCIATION)), null);       // FIXME: clientState=null
    }

    // ---

    @Override
    public boolean isTopicInTopicmap(long topicmapId, long topicId) {
        return fetchTopicRefAssociation(topicmapId, topicId) != null;
    }

    // ---

    @PUT
    @Path("/{id}/topic/{topic_id}")
    @Override
    public void setViewProperties(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                    CompositeValueModel viewProps) {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            storeStandardViewProperties(topicmapId, topicId, viewProps);
            storeCustomViewProperties(topicmapId, topicId, viewProps);
            //
            tx.success();
        } catch (Exception e) {
            throw new RuntimeException("Storing view properties of topic " + topicId + " failed " +
                "(viewProps=" + viewProps + ")", e);
        } finally {
            tx.finish();
        }
    }


    @PUT
    @Path("/{id}/topic/{topic_id}/{x}/{y}")
    @Override
    public void setTopicPosition(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                   @PathParam("x") int x, @PathParam("y") int y) {
        storeStandardViewProperties(topicmapId, topicId, new StandardViewProperties(x, y));
    }

    @PUT
    @Path("/{id}/topic/{topic_id}/{visibility}")
    @Override
    public void setTopicVisibility(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                     @PathParam("visibility") boolean visibility) {
        storeStandardViewProperties(topicmapId, topicId, new StandardViewProperties(visibility));
    }

    @DELETE
    @Path("/{id}/association/{assoc_id}")
    @Override
    public void removeAssociationFromTopicmap(@PathParam("id") long topicmapId, @PathParam("assoc_id") long assocId) {
        fetchAssociationRefAssociation(topicmapId, assocId).delete(new Directives());
    }

    // ---

    @PUT
    @Path("/{id}")
    @Override
    public void setClusterPosition(@PathParam("id") long topicmapId, ClusterCoords coords) {
        for (ClusterCoords.Entry entry : coords) {
            setTopicPosition(topicmapId, entry.topicId, entry.x, entry.y);
        }
    }

    @PUT
    @Path("/{id}/translation/{x}/{y}")
    @Override
    public void setTopicmapTranslation(@PathParam("id") long topicmapId, @PathParam("x") int transX,
                                                                         @PathParam("y") int transY) {
        try {
            CompositeValueModel topicmapState = new CompositeValueModel()
                .put("dm4.topicmaps.state", new CompositeValueModel()
                    .put("dm4.topicmaps.translation", new CompositeValueModel()
                        .put("dm4.topicmaps.translation_x", transX)
                        .put("dm4.topicmaps.translation_y", transY)));
            TopicModel model = new TopicModel(topicmapId, topicmapState);
            // workaround the "lost URI" problem (see #311) ### FIXME
            model.setUri(dms.getTopic(topicmapId, false).getUri());
            //
            dms.updateTopic(model, null);
        } catch (Exception e) {
            throw new RuntimeException("Setting translation of topicmap " + topicmapId + " failed (transX=" +
                transX + ", transY=" + transY + ")", e);
        }
    }

    // ---

    @Override
    public void registerTopicmapRenderer(TopicmapRenderer renderer) {
        logger.info("### Registering topicmap renderer \"" + renderer.getClass().getName() + "\"");
        topicmapRenderers.put(renderer.getUri(), renderer);
    }

    // ---

    @Override
    public void registerViewmodelCustomizer(ViewmodelCustomizer customizer) {
        logger.info("### Registering viewmodel customizer \"" + customizer.getClass().getName() + "\"");
        viewmodelCustomizers.add(customizer);
    }

    @Override
    public void unregisterViewmodelCustomizer(ViewmodelCustomizer customizer) {
        logger.info("### Unregistering viewmodel customizer \"" + customizer.getClass().getName() + "\"");
        if (!viewmodelCustomizers.remove(customizer)) {
            throw new RuntimeException("Unregistering viewmodel customizer failed (customizer=" + customizer + ")");
        }
    }

    // ---

    // Note: not part of topicmaps service
    @GET
    @Path("/{id}")
    @Produces("text/html")
    public InputStream getTopicmapInWebclient() {
        // Note: the path parameter is evaluated at client-side
        return invokeWebclient();
    }

    // Note: not part of topicmaps service
    @GET
    @Path("/{id}/topic/{topic_id}")
    @Produces("text/html")
    public InputStream getTopicmapAndTopicInWebclient() {
        // Note: the path parameters are evaluated at client-side
        return invokeWebclient();
    }



    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void postInstall() {
        createTopicmap(DEFAULT_TOPICMAP_NAME, DEFAULT_TOPICMAP_URI, DEFAULT_TOPICMAP_RENDERER, null);
        // Note: null is passed as clientState. On post-install we have no clientState.
        // The workspace assignment is made by the Access Control plugin on all-plugins-active.
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    private void storeStandardViewProperties(long topicmapId, long topicId, CompositeValueModel viewProps) {
        fetchTopicRefAssociation(topicmapId, topicId).setCompositeValue(viewProps, null, new Directives());
    }                                                                           // clientState=null

    // ### Note: the topicmapId parameter is not used. Per-topicmap custom view properties not yet supported.
    private void storeCustomViewProperties(long topicmapId, long topicId, CompositeValueModel viewProps) {
        invokeViewmodelCustomizers(topicId, viewProps);
    }

    // ---

    private Association fetchTopicRefAssociation(long topicmapId, long topicId) {
        return dms.getAssociation(TOPIC_MAPCONTEXT, topicmapId, topicId,
            ROLE_TYPE_TOPICMAP, ROLE_TYPE_TOPIC, false);        // fetchComposite=false
    }

    private Association fetchAssociationRefAssociation(long topicmapId, long assocId) {
        return dms.getAssociationBetweenTopicAndAssociation(ASSOCIATION_MAPCONTEXT, topicmapId, assocId,
            ROLE_TYPE_TOPICMAP, ROLE_TYPE_ASSOCIATION, false);  // fetchComposite=false
    }

    // ---

    // ### There is a copy in TopicmapViewmodel
    private void invokeViewmodelCustomizers(long topicId, CompositeValueModel viewProps) {
        Topic topic = dms.getTopic(topicId, false);             // fetchComposite=false
        for (ViewmodelCustomizer customizer : viewmodelCustomizers) {
            invokeViewmodelCustomizer(customizer, topic, viewProps);
        }
    }

    // ### There is a principal copy in TopicmapViewmodel
    private void invokeViewmodelCustomizer(ViewmodelCustomizer customizer, Topic topic, CompositeValueModel viewProps) {
        try {
            customizer.storeViewProperties(topic, viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Invoking viewmodel customizer for topic " + topic.getId() + " failed " +
                "(customizer=\"" + customizer.getClass().getName() + "\", method=\"storeViewProperties\")", e);
        }
    }

    // ---

    private TopicmapRenderer getTopicmapRenderer(String rendererUri) {
        TopicmapRenderer renderer = topicmapRenderers.get(rendererUri);
        //
        if (renderer == null) {
            throw new RuntimeException("\"" + rendererUri + "\" is an unknown topicmap renderer");
        }
        //
        return renderer;
    }

    // ---

    private InputStream invokeWebclient() {
        try {
            return dms.getPlugin("de.deepamehta.webclient").getResourceAsStream("web/index.html");
        } catch (Exception e) {
            throw new RuntimeException("Invoking the webclient failed", e);
        }
    }

    // --------------------------------------------------------------------------------------------- Private Inner Class

    private class StandardViewProperties extends CompositeValueModel {

        private StandardViewProperties(int x, int y, boolean visibility) {
            put(x, y);
            put(visibility);
        }

        private StandardViewProperties(int x, int y) {
            put(x, y);
        }


        private StandardViewProperties(boolean visibility) {
            put(visibility);
        }

        // ---

        private void put(int x, int y) {
            put("dm4.topicmaps.x", x);
            put("dm4.topicmaps.y", y);
        }

        private void put(boolean visibility) {
            put("dm4.topicmaps.visibility", visibility);
        }
    }
}
