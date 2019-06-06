package systems.dmx.storage.neo4j;

import systems.dmx.core.model.AssocModel;
import systems.dmx.core.model.AssocPlayerModel;
import systems.dmx.core.model.DMXObjectModel;
import systems.dmx.core.model.PlayerModel;
import systems.dmx.core.model.RelatedAssocModel;
import systems.dmx.core.model.RelatedTopicModel;
import systems.dmx.core.model.SimpleValue;
import systems.dmx.core.model.TopicModel;
import systems.dmx.core.model.TopicPlayerModel;
import systems.dmx.core.service.ModelFactory;
import systems.dmx.core.storage.spi.DMXStorage;
import systems.dmx.core.storage.spi.DMXTransaction;
import systems.dmx.core.util.JavaUtils;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import static org.neo4j.helpers.collection.MapUtil.stringMap;
import org.neo4j.index.lucene.QueryContext;
import org.neo4j.index.lucene.ValueContext;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



public class Neo4jStorage implements DMXStorage {

    // ------------------------------------------------------------------------------------------------------- Constants

    // --- DB Property Keys ---
    private static final String KEY_NODE_TYPE = "nodeType";
    private static final String KEY_VALUE     = "value";

    // --- Content Index Keys ---
    private static final String KEY_URI      = "uri";                       // used as property key as well
    private static final String KEY_TPYE_URI = "typeUri";                   // used as property key as well
    private static final String KEY_FULLTEXT = "fulltext";

    // --- Association Metadata Index Keys ---
    private static final String KEY_ASSOC_ID       = "assocId";
    private static final String KEY_ASSOC_TPYE_URI = "assocTypeUri";
    // role 1 & 2
    private static final String KEY_ROLE_TPYE_URI   = "roleTypeUri";        // "1" or "2" is appended programatically
    private static final String KEY_PLAYER_TPYE     = "playerType";         // "1" or "2" is appended programatically
    private static final String KEY_PLAYER_ID       = "playerId";           // "1" or "2" is appended programatically
    private static final String KEY_PLAYER_TYPE_URI = "playerTypeUri";      // "1" or "2" is appended programatically

    // Note: URIs, type URIs, and properties are only KEY indexed.
    // Topic/assoc values are indexed using all 3 modes.

    private enum IndexMode {
        KEY, FULLTEXT, FULLTEXT_KEY;
    }

    // ---------------------------------------------------------------------------------------------- Instance Variables

            GraphDatabaseService neo4j = null;
    private RelationtypeCache relTypeCache;

    private Index<Node> topicContentExact;      // topic URI, topic type URI, topic value (index mode KEY), properties
    private Index<Node> topicContentFulltext;   // topic value (index modes FULLTEXT or FULLTEXT_KEY)
    private Index<Node> assocContentExact;      // assoc URI, assoc type URI, assoc value (index mode KEY), properties
    private Index<Node> assocContentFulltext;   // assoc value (index modes FULLTEXT or FULLTEXT_KEY)
    private Index<Node> assocMetadata;

    private ModelFactory mf;

    private final Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    Neo4jStorage(String databasePath, ModelFactory mf) {
        try {
            this.neo4j = new GraphDatabaseFactory().newEmbeddedDatabase(databasePath);
            this.relTypeCache = new RelationtypeCache(neo4j);
            // indexes
            this.topicContentExact    = createExactIndex("topic-content-exact");
            this.topicContentFulltext = createFulltextIndex("topic-content-fulltext");
            this.assocContentExact    = createExactIndex("assoc-content-exact");
            this.assocContentFulltext = createFulltextIndex("assoc-content-fulltext");
            this.assocMetadata = createExactIndex("assoc-metadata");
            //
            this.mf = mf;
        } catch (Exception e) {
            if (neo4j != null) {
                shutdown();
            }
            throw new RuntimeException("Creating the Neo4j instance and indexes failed", e);
        }
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ****************************************
    // *** DMXStorage Implementation ***
    // ****************************************



    // === Topics ===

    @Override
    public TopicModel fetchTopic(long topicId) {
        return buildTopic(fetchTopicNode(topicId));
    }

    @Override
    public TopicModel fetchTopic(String key, Object value) {
        Node node = topicContentExact.get(key, value).getSingle();
        return node != null ? buildTopic(node) : null;
    }

    @Override
    public List<TopicModel> fetchTopics(String key, Object value) {
        return buildTopics(topicContentExact.query(key, value));
    }

    @Override
    public List<TopicModel> queryTopics(Object value) {
        return queryTopics(null, value);
    }

    @Override
    public List<TopicModel> queryTopics(String key, Object value) {
        if (key == null) {
            key = KEY_FULLTEXT;
        }
        if (value == null) {
            throw new IllegalArgumentException("Tried to call queryTopics() with a null value Object (key=\"" + key +
                "\")");
        }
        //
        return buildTopics(topicContentFulltext.query(key, value));
    }

    @Override
    public Iterator<TopicModel> fetchAllTopics() {
        return new TopicModelIterator(this);
    }

    // ---

    @Override
    public void storeTopic(TopicModel topicModel) {
        setDefaults(topicModel);
        //
        // 1) update DB
        Node topicNode = neo4j.createNode();
        topicNode.setProperty(KEY_NODE_TYPE, "topic");
        //
        storeAndIndexTopicUri(topicNode, topicModel.getUri());
        storeAndIndexTopicTypeUri(topicNode, topicModel.getTypeUri());
        //
        // 2) update model
        topicModel.setId(topicNode.getId());
    }

    @Override
    public void storeTopicUri(long topicId, String uri) {
        storeAndIndexTopicUri(fetchTopicNode(topicId), uri);
    }

    // Note: a storage implementation is not responsible for maintaining the "Instantiation" associations.
    // This is performed at the application layer.
    @Override
    public void storeTopicTypeUri(long topicId, String topicTypeUri) {
        Node topicNode = fetchTopicNode(topicId);
        //
        // 1) update DB and content index
        storeAndIndexTopicTypeUri(topicNode, topicTypeUri);
        //
        // 2) update association metadata index
        reindexTypeUri(topicNode, topicTypeUri);
    }

    @Override
    public void storeTopicValue(long topicId, SimpleValue value, String indexKey, boolean isHtmlValue) {
        if (indexKey == null) {
            throw new IllegalArgumentException("indexKey must be not null (value=\"" + value + "\")");
        }
        //
        Node topicNode = fetchTopicNode(topicId);
        // store
        topicNode.setProperty(KEY_VALUE, value.value());
        // index
        indexTopicNodeValue(topicNode, indexKey, value.value(), isHtmlValue);
    }

    // ---

    @Override
    public void deleteTopic(long topicId) {
        // 1) update DB
        Node topicNode = fetchTopicNode(topicId);
        topicNode.delete();
        //
        // 2) update index
        removeTopicFromIndex(topicNode);
    }



    // === Associations ===

    @Override
    public AssocModel fetchAssoc(long assocId) {
        return buildAssociation(fetchAssocNode(assocId));
    }

    @Override
    public AssocModel fetchAssoc(String key, Object value) {
        Node node = assocContentExact.get(key, value).getSingle();
        return node != null ? buildAssociation(node) : null;
    }

    @Override
    public List<AssocModel> fetchAssocs(String key, Object value) {
        return buildAssociations(assocContentExact.query(key, value));
    }

    @Override
    public List<AssocModel> fetchAssocs(String assocTypeUri, long topicId1, long topicId2, String roleTypeUri1,
                                                                                           String roleTypeUri2) {
        return queryAssociationIndex(
            assocTypeUri,
            roleTypeUri1, NodeType.TOPIC, topicId1, null,
            roleTypeUri2, NodeType.TOPIC, topicId2, null
        );
    }

    @Override
    public List<AssocModel> fetchAssocsBetweenTopicAndAssoc(String assocTypeUri, long topicId, long assocId,
                                                            String topicRoleTypeUri, String assocRoleTypeUri) {
        return queryAssociationIndex(
            assocTypeUri,
            topicRoleTypeUri, NodeType.TOPIC, topicId, null,
            assocRoleTypeUri, NodeType.ASSOC, assocId, null
        );
    }

    @Override
    public Iterator<AssocModel> fetchAllAssociations() {
        return new AssociationModelIterator(this);
    }

    @Override
    public List<PlayerModel> fetchRoleModels(long assocId) {
        return buildRoleModels(fetchAssocNode(assocId));
    }

    // ---

    @Override
    public void storeAssoc(AssocModel assocModel) {
        setDefaults(assocModel);
        //
        // 1) update DB
        Node assocNode = neo4j.createNode();
        assocNode.setProperty(KEY_NODE_TYPE, "assoc");
        //
        storeAndIndexAssociationUri(assocNode, assocModel.getUri());
        storeAndIndexAssocTypeUri(assocNode, assocModel.getTypeUri());
        //
        PlayerModel role1 = assocModel.getRoleModel1();
        PlayerModel role2 = assocModel.getRoleModel2();
        Node playerNode1 = storePlayerRelationship(assocNode, role1);
        Node playerNode2 = storePlayerRelationship(assocNode, role2);
        //
        // 2) update index
        indexAssociation(assocNode, role1.getRoleTypeUri(), playerNode1,
                                    role2.getRoleTypeUri(), playerNode2);
        // 3) update model
        assocModel.setId(assocNode.getId());
    }

    @Override
    public void storeAssocUri(long assocId, String uri) {
        storeAndIndexAssociationUri(fetchAssocNode(assocId), uri);
    }

    // Note: a storage implementation is not responsible for maintaining the "Instantiation" associations.
    // This is performed at the application layer.
    @Override
    public void storeAssocTypeUri(long assocId, String assocTypeUri) {
        Node assocNode = fetchAssocNode(assocId);
        //
        // 1) update DB and content index
        storeAndIndexAssocTypeUri(assocNode, assocTypeUri);
        //
        // 2) update association metadata index
        indexAssocType(assocNode, assocTypeUri);    // update association entry itself
        reindexTypeUri(assocNode, assocTypeUri);    // update all association entries the association is a player of
    }

    @Override
    public void storeAssocValue(long assocId, SimpleValue value, String indexKey, boolean isHtmlValue) {
        if (indexKey == null) {
            throw new IllegalArgumentException("indexKey must be not null (value=\"" + value + "\")");
        }
        //
        Node assocNode = fetchAssocNode(assocId);
        // store
        assocNode.setProperty(KEY_VALUE, value.value());
        // index
        indexAssociationNodeValue(assocNode, indexKey, value.value(), isHtmlValue);
    }

    @Override
    public void storeRoleTypeUri(long assocId, long playerId, String roleTypeUri) {
        Node assocNode = fetchAssocNode(assocId);
        //
        // 1) update DB
        fetchRelationship(assocNode, playerId).delete();                                        // delete relationship
        assocNode.createRelationshipTo(fetchNode(playerId), getRelationshipType(roleTypeUri));  // create new one
        //
        // 2) update association metadata index
        indexAssociationRoleType(assocNode, playerId, roleTypeUri);
    }

    // ---

    @Override
    public void deleteAssociation(long assocId) {
        // 1) update DB
        Node assocNode = fetchAssocNode(assocId);
        // delete the 2 player relationships
        for (Relationship rel : fetchRelationships(assocNode)) {
            rel.delete();
        }
        //
        assocNode.delete();
        //
        // 2) update index
        removeAssociationFromIndex(assocNode);
    }



    // === Generic Object ===

    @Override
    public DMXObjectModel fetchObject(long id) {
        Node node = fetchNode(id);
        NodeType nodeType = NodeType.of(node);
        switch (nodeType) {
        case TOPIC:
            return buildTopic(node);
        case ASSOC:
            return buildAssociation(node);
        default:
            throw new RuntimeException("Unexpected node type: " + nodeType);
        }
    }



    // === Traversal ===

    @Override
    public List<AssocModel> fetchTopicAssociations(long topicId) {
        return fetchAssocs(fetchTopicNode(topicId));
    }

    @Override
    public List<AssocModel> fetchAssocAssociations(long assocId) {
        return fetchAssocs(fetchAssocNode(assocId));
    }

    // ---

    @Override
    public List<RelatedTopicModel> fetchTopicRelatedTopics(long topicId, String assocTypeUri, String myRoleTypeUri,
                                                           String othersRoleTypeUri, String othersTopicTypeUri) {
        return buildRelatedTopics(queryAssociationIndex(
            assocTypeUri,
            myRoleTypeUri,     NodeType.TOPIC, topicId, null,
            othersRoleTypeUri, NodeType.TOPIC, -1,      othersTopicTypeUri
        ), topicId);
    }

    @Override
    public List<RelatedAssocModel> fetchTopicRelatedAssocs(long topicId, String assocTypeUri, String myRoleTypeUri,
                                                           String othersRoleTypeUri, String othersAssocTypeUri) {
        return buildRelatedAssocs(queryAssociationIndex(
            assocTypeUri,
            myRoleTypeUri,     NodeType.TOPIC, topicId, null,
            othersRoleTypeUri, NodeType.ASSOC, -1,      othersAssocTypeUri
        ), topicId);
    }

    // ---

    @Override
    public List<RelatedTopicModel> fetchAssocRelatedTopics(long assocId, String assocTypeUri, String myRoleTypeUri,
                                                           String othersRoleTypeUri, String othersTopicTypeUri) {
        return buildRelatedTopics(queryAssociationIndex(
            assocTypeUri,
            myRoleTypeUri,     NodeType.ASSOC, assocId, null,
            othersRoleTypeUri, NodeType.TOPIC, -1,      othersTopicTypeUri
        ), assocId);
    }

    @Override
    public List<RelatedAssocModel> fetchAssocRelatedAssocs(long assocId, String assocTypeUri, String myRoleTypeUri,
                                                           String othersRoleTypeUri, String othersAssocTypeUri) {
        return buildRelatedAssocs(queryAssociationIndex(
            assocTypeUri,
            myRoleTypeUri,     NodeType.ASSOC, assocId, null,
            othersRoleTypeUri, NodeType.ASSOC, -1,      othersAssocTypeUri
        ), assocId);
    }

    // ---

    @Override
    public List<RelatedTopicModel> fetchRelatedTopics(long id, String assocTypeUri, String myRoleTypeUri,
                                                      String othersRoleTypeUri, String othersTopicTypeUri) {
        return buildRelatedTopics(queryAssociationIndex(
            assocTypeUri,
            myRoleTypeUri,     null,           id, null,
            othersRoleTypeUri, NodeType.TOPIC, -1, othersTopicTypeUri
        ), id);
    }

    @Override
    public List<RelatedAssocModel> fetchRelatedAssocs(long id, String assocTypeUri, String myRoleTypeUri,
                                                      String othersRoleTypeUri, String othersAssocTypeUri) {
        return buildRelatedAssocs(queryAssociationIndex(
            assocTypeUri,
            myRoleTypeUri,     null,           id, null,
            othersRoleTypeUri, NodeType.ASSOC, -1, othersAssocTypeUri
        ), id);
    }



    // === Properties ===

    @Override
    public Object fetchProperty(long id, String propUri) {
        return fetchNode(id).getProperty(propUri);
    }

    @Override
    public boolean hasProperty(long id, String propUri) {
        return fetchNode(id).hasProperty(propUri);
    }

    // ---

    @Override
    public List<TopicModel> fetchTopicsByProperty(String propUri, Object propValue) {
        return buildTopics(queryIndexByProperty(topicContentExact, propUri, propValue));
    }

    @Override
    public List<TopicModel> fetchTopicsByPropertyRange(String propUri, Number from, Number to) {
        return buildTopics(queryIndexByPropertyRange(topicContentExact, propUri, from, to));
    }

    @Override
    public List<AssocModel> fetchAssocsByProperty(String propUri, Object propValue) {
        return buildAssociations(queryIndexByProperty(assocContentExact, propUri, propValue));
    }

    @Override
    public List<AssocModel> fetchAssocsByPropertyRange(String propUri, Number from, Number to) {
        return buildAssociations(queryIndexByPropertyRange(assocContentExact, propUri, from, to));
    }

    // ---

    @Override
    public void storeTopicProperty(long topicId, String propUri, Object propValue, boolean addToIndex) {
        Index<Node> exactIndex = addToIndex ? topicContentExact : null;
        storeAndIndexExactValue(fetchTopicNode(topicId), propUri, propValue, exactIndex);
    }

    @Override
    public void storeAssocProperty(long assocId, String propUri, Object propValue, boolean addToIndex) {
        Index<Node> exactIndex = addToIndex ? assocContentExact : null;
        storeAndIndexExactValue(fetchAssocNode(assocId), propUri, propValue, exactIndex);
    }

    // ---

    @Override
    public void indexTopicProperty(long topicId, String propUri, Object propValue) {
        indexExactValue(fetchTopicNode(topicId), propUri, propValue, topicContentExact);
    }

    @Override
    public void indexAssociationProperty(long assocId, String propUri, Object propValue) {
        indexExactValue(fetchAssocNode(assocId), propUri, propValue, assocContentExact);
    }

    // ---

    @Override
    public void deleteTopicProperty(long topicId, String propUri) {
        Node topicNode = fetchTopicNode(topicId);
        topicNode.removeProperty(propUri);
        removeTopicPropertyFromIndex(topicNode, propUri);
    }

    @Override
    public void deleteAssociationProperty(long assocId, String propUri) {
        Node assocNode = fetchAssocNode(assocId);
        assocNode.removeProperty(propUri);
        removeAssociationPropertyFromIndex(assocNode, propUri);
    }



    // === DB ===

    @Override
    public DMXTransaction beginTx() {
        return new Neo4jTransactionAdapter(neo4j);
    }

    @Override
    public boolean setupRootNode() {
        try {
            Node rootNode = fetchNode(0);
            //
            if (rootNode.getProperty(KEY_NODE_TYPE, null) != null) {
                return false;
            }
            //
            rootNode.setProperty(KEY_NODE_TYPE, "topic");
            rootNode.setProperty(KEY_VALUE, "Meta Type");
            storeAndIndexTopicUri(rootNode, "dmx.core.meta_type");
            storeAndIndexTopicTypeUri(rootNode, "dmx.core.meta_meta_type");
            //
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Setting up the root node (0) failed", e);
        }
    }

    @Override
    public void shutdown() {
        neo4j.shutdown();
    }

    // ---

    @Override
    public Object getDatabaseVendorObject() {
        return neo4j;
    }

    @Override
    public Object getDatabaseVendorObject(long objectId) {
        return fetchNode(objectId);
    }

    // ---

    @Override
    public ModelFactory getModelFactory() {
        return mf;
    }

    // ------------------------------------------------------------------------------------------------- Private Methods



    // === Value Storage ===

    private void storeAndIndexTopicUri(Node topicNode, String uri) {
        checkUriUniqueness(uri);
        storeAndIndexExactValue(topicNode, KEY_URI, uri, topicContentExact);
    }

    private void storeAndIndexAssociationUri(Node assocNode, String uri) {
        checkUriUniqueness(uri);
        storeAndIndexExactValue(assocNode, KEY_URI, uri, assocContentExact);
    }

    // ---

    private void storeAndIndexTopicTypeUri(Node topicNode, String topicTypeUri) {
        storeAndIndexExactValue(topicNode, KEY_TPYE_URI, topicTypeUri, topicContentExact);
    }

    private void storeAndIndexAssocTypeUri(Node assocNode, String assocTypeUri) {
        storeAndIndexExactValue(assocNode, KEY_TPYE_URI, assocTypeUri, assocContentExact);
    }

    // ---

    /**
     * Stores a node value under the specified key and adds the value to the specified index (under the same key).
     * <code>IndexMode.KEY</code> is used for indexing.
     * <p>
     * Used for URIs, type URIs, and properties.
     * Note: for URIs and type URIs indexing is mandatory, for properties indexing is optional.
     *
     * @param   node        a topic node, or an association node.
     * @param   exactIndex  the index to add the value to. If <code>null</code> no indexing is performed.
     */
    private void storeAndIndexExactValue(Node node, String key, Object value, Index<Node> exactIndex) {
        // store
        node.setProperty(key, value);
        // index
        if (exactIndex != null) {
            indexExactValue(node, key, value, exactIndex);
        }
    }

    /**
     * Indexes a node value under the specified key. <code>IndexMode.KEY</code> is used.
     * <p>
     * Used for URIs, type URIs, and properties.
     *
     * @param   node        a topic node, or an association node.
     * @param   exactIndex  the index to use. Must not be <code>null</code>.
     */
    private void indexExactValue(Node node, String key, Object value, Index<Node> exactIndex) {
        // Note: numbers are indexed numerically to allow range queries.
        if (value instanceof Number) {
            value = ValueContext.numeric((Number) value);
        }
        indexNodeValue(node, value, IndexMode.KEY, key, exactIndex, null);      // fulltextIndex=null
    }

    // ---

    private void indexTopicNodeValue(Node topicNode, String indexKey, Object value, boolean isHtmlValue) {
        indexNodeValue(topicNode, value, IndexMode.KEY,          indexKey, topicContentExact, topicContentFulltext);
        // TODO: don't fulltext index numbers/booleans?
        value = getIndexValue(value, isHtmlValue);
        indexNodeValue(topicNode, value, IndexMode.FULLTEXT,     indexKey, topicContentExact, topicContentFulltext);
        indexNodeValue(topicNode, value, IndexMode.FULLTEXT_KEY, indexKey, topicContentExact, topicContentFulltext);
    }

    private void indexAssociationNodeValue(Node assocNode, String indexKey, Object value, boolean isHtmlValue) {
        indexNodeValue(assocNode, value, IndexMode.KEY,          indexKey, assocContentExact, assocContentFulltext);
        // TODO: don't fulltext index numbers/booleans?
        value = getIndexValue(value, isHtmlValue);
        indexNodeValue(assocNode, value, IndexMode.FULLTEXT,     indexKey, assocContentExact, assocContentFulltext);
        indexNodeValue(assocNode, value, IndexMode.FULLTEXT_KEY, indexKey, assocContentExact, assocContentFulltext);
    }

    // ---

    /**
     * HTML tags are stripped from HTML values. Non-HTML values are returned directly.
     */
    private Object getIndexValue(Object value, boolean isHtmlValue) {
        return isHtmlValue ? JavaUtils.stripHTML((String) value) : value;
    }



    // === Indexing ===

    private void indexNodeValue(Node node, Object value, IndexMode indexMode, String indexKey,
                                                         Index<Node> exactIndex, Index<Node> fulltextIndex) {
        if (indexMode == IndexMode.KEY) {
            exactIndex.remove(node, indexKey);              // remove old
            exactIndex.add(node, indexKey, value);          // index new
        } else if (indexMode == IndexMode.FULLTEXT) {
            fulltextIndex.remove(node, KEY_FULLTEXT);       // remove old
            fulltextIndex.add(node, KEY_FULLTEXT, value);   // index new
        } else if (indexMode == IndexMode.FULLTEXT_KEY) {
            fulltextIndex.remove(node, indexKey);           // remove old
            fulltextIndex.add(node, indexKey, value);       // index new
        } else {
            throw new RuntimeException("Unexpected index mode: \"" + indexMode + "\"");
        }
    }

    // ---

    private void indexAssociation(Node assocNode, String roleTypeUri1, Node playerNode1,
                                                  String roleTypeUri2, Node playerNode2) {
        indexAssociationId(assocNode);
        indexAssocType(assocNode, typeUri(assocNode));
        //
        indexAssociationRole(assocNode, 1, roleTypeUri1, playerNode1);
        indexAssociationRole(assocNode, 2, roleTypeUri2, playerNode2);
    }

    private void indexAssociationId(Node assocNode) {
        assocMetadata.add(assocNode, KEY_ASSOC_ID, assocNode.getId());
    }

    private void indexAssocType(Node assocNode, String assocTypeUri) {
        reindexValue(assocNode, KEY_ASSOC_TPYE_URI, assocTypeUri);
    }

    private void indexAssociationRole(Node assocNode, int pos, String roleTypeUri, Node playerNode) {
        assocMetadata.add(assocNode, KEY_ROLE_TPYE_URI + pos, roleTypeUri);
        assocMetadata.add(assocNode, KEY_PLAYER_TPYE + pos, NodeType.of(playerNode).stringify());
        assocMetadata.add(assocNode, KEY_PLAYER_ID + pos, playerNode.getId());
        assocMetadata.add(assocNode, KEY_PLAYER_TYPE_URI + pos, typeUri(playerNode));
    }

    // ---

    private void indexAssociationRoleType(Node assocNode, long playerId, String roleTypeUri) {
        int pos = lookupPlayerPosition(assocNode.getId(), playerId);
        reindexValue(assocNode, KEY_ROLE_TPYE_URI, pos, roleTypeUri);
    }

    private int lookupPlayerPosition(long assocId, long playerId) {
        boolean pos1 = isPlayerAtPosition(1, assocId, playerId);
        boolean pos2 = isPlayerAtPosition(2, assocId, playerId);
        if (pos1 && pos2) {
            throw new RuntimeException("Ambiguity: both players have ID " + playerId + " in association " + assocId);
        } else if (pos1) {
            return 1;
        } else if (pos2) {
            return 2;
        } else {
            throw new IllegalArgumentException("ID " + playerId + " is not a player in association " + assocId);
        }
    }

    private boolean isPlayerAtPosition(int pos, long assocId, long playerId) {
        BooleanQuery query = new BooleanQuery();
        addTermQuery(KEY_ASSOC_ID, assocId, query);
        addTermQuery(KEY_PLAYER_ID + pos, playerId, query);
        return assocMetadata.query(query).getSingle() != null;
    }

    // ---

    private void reindexTypeUri(Node playerNode, String typeUri) {
        reindexTypeUri(1, playerNode, typeUri);
        reindexTypeUri(2, playerNode, typeUri);
    }

    /**
     * Re-indexes the KEY_PLAYER_TYPE_URI of all associations in which the specified node
     * is a player at the specified position.
     *
     * @param   playerNode  a topic node or an association node.
     * @param   typeUri     the new type URI to be indexed for the player node.
     */
    private void reindexTypeUri(int pos, Node playerNode, String typeUri) {
        for (Node assocNode : lookupAssociations(pos, playerNode)) {
            reindexValue(assocNode, KEY_PLAYER_TYPE_URI, pos, typeUri);
        }
    }

    private IndexHits<Node> lookupAssociations(int pos, Node playerNode) {
        return assocMetadata.get(KEY_PLAYER_ID + pos, playerNode.getId());
    }

    // ---

    private void reindexValue(Node assocNode, String key, int pos, String value) {
        reindexValue(assocNode, key + pos, value);
    }

    private void reindexValue(Node assocNode, String key, String value) {
        assocMetadata.remove(assocNode, key);
        assocMetadata.add(assocNode, key, value);
    }

    // --- Query indexes ---

    private IndexHits<Node> queryIndexByProperty(Index<Node> index, String propUri, Object propValue) {
        // Note: numbers must be queried as numeric value as they are indexed numerically.
        if (propValue instanceof Number) {
            propValue = ValueContext.numeric((Number) propValue);
        }
        return index.get(propUri, propValue);
    }

    private IndexHits<Node> queryIndexByPropertyRange(Index<Node> index, String propUri, Number from, Number to) {
        return index.query(buildNumericRangeQuery(propUri, from, to));
    }

    // ---

    private List<AssocModel> queryAssociationIndex(String assocTypeUri,
                                     String roleTypeUri1, NodeType playerType1, long playerId1, String playerTypeUri1,
                                     String roleTypeUri2, NodeType playerType2, long playerId2, String playerTypeUri2) {
        return buildAssociations(assocMetadata.query(buildAssociationQuery(assocTypeUri,
            roleTypeUri1, playerType1, playerId1, playerTypeUri1,
            roleTypeUri2, playerType2, playerId2, playerTypeUri2
        )));
    }

    // --- Build index queries ---

    private QueryContext buildNumericRangeQuery(String propUri, Number from, Number to) {
        return QueryContext.numericRange(propUri, from, to);
    }

    // ---

    private Query buildAssociationQuery(String assocTypeUri,
                                     String roleTypeUri1, NodeType playerType1, long playerId1, String playerTypeUri1,
                                     String roleTypeUri2, NodeType playerType2, long playerId2, String playerTypeUri2) {
        // query bidirectional
        BooleanQuery direction1 = new BooleanQuery();
        addRole(direction1, 1, roleTypeUri1, playerType1, playerId1, playerTypeUri1);
        addRole(direction1, 2, roleTypeUri2, playerType2, playerId2, playerTypeUri2);
        BooleanQuery direction2 = new BooleanQuery();
        addRole(direction2, 1, roleTypeUri2, playerType2, playerId2, playerTypeUri2);
        addRole(direction2, 2, roleTypeUri1, playerType1, playerId1, playerTypeUri1);
        //
        BooleanQuery roleQuery = new BooleanQuery();
        roleQuery.add(direction1, Occur.SHOULD);
        roleQuery.add(direction2, Occur.SHOULD);
        //
        BooleanQuery query = new BooleanQuery();
        if (assocTypeUri != null) {
            addTermQuery(KEY_ASSOC_TPYE_URI, assocTypeUri, query);
        }
        query.add(roleQuery, Occur.MUST);
        //
        return query;
    }

    private void addRole(BooleanQuery query, int pos, String roleTypeUri, NodeType playerType, long playerId,
                                                                                               String playerTypeUri) {
        if (roleTypeUri != null)   addTermQuery(KEY_ROLE_TPYE_URI + pos,   roleTypeUri,   query);
        if (playerType != null)    addTermQuery(KEY_PLAYER_TPYE + pos,     playerType,    query);
        if (playerId != -1)        addTermQuery(KEY_PLAYER_ID + pos,       playerId,      query);
        if (playerTypeUri != null) addTermQuery(KEY_PLAYER_TYPE_URI + pos, playerTypeUri, query);
    }

    // ---

    private void addTermQuery(String key, long value, BooleanQuery query) {
        addTermQuery(key, Long.toString(value), query);
    }

    private void addTermQuery(String key, NodeType nodeType, BooleanQuery query) {
        addTermQuery(key, nodeType.stringify(), query);
    }

    private void addTermQuery(String key, String value, BooleanQuery query) {
        query.add(new TermQuery(new Term(key, value)), Occur.MUST);
    }

    // --- Remove index entries ---

    private void removeTopicFromIndex(Node topicNode) {
        topicContentExact.remove(topicNode);
        topicContentFulltext.remove(topicNode);
    }

    private void removeAssociationFromIndex(Node assocNode) {
        assocContentExact.remove(assocNode);
        assocContentFulltext.remove(assocNode);
        //
        assocMetadata.remove(assocNode);
    }

    // ---

    private void removeTopicPropertyFromIndex(Node topicNode, String propUri) {
        topicContentExact.remove(topicNode, propUri);
    }

    private void removeAssociationPropertyFromIndex(Node assocNode, String propUri) {
        assocContentExact.remove(assocNode, propUri);
    }

    // --- Create indexes ---

    private Index<Node> createExactIndex(String name) {
        return neo4j.index().forNodes(name);
    }

    private Index<Node> createFulltextIndex(String name) {
        if (neo4j.index().existsForNodes(name)) {
            return neo4j.index().forNodes(name);
        } else {
            Map<String, String> configuration = stringMap(IndexManager.PROVIDER, "lucene", "type", "fulltext");
            return neo4j.index().forNodes(name, configuration);
        }
    }



    // === Helper ===

    // --- Neo4j -> DMX Bridge ---

    TopicModel buildTopic(Node topicNode) {
        try {
            return mf.newTopicModel(
                topicNode.getId(),
                uri(topicNode),
                typeUri(topicNode),
                simpleValue(topicNode),
                null    // childTopics=null
            );
        } catch (Exception e) {
            throw new RuntimeException("Building a TopicModel failed (id=" + topicNode.getId() + ", typeUri=" +
                typeUri(topicNode) + ")");
        }
    }

    private List<TopicModel> buildTopics(Iterable<Node> topicNodes) {
        List<TopicModel> topics = new ArrayList();
        for (Node topicNode : topicNodes) {
            topics.add(buildTopic(topicNode));
        }
        return topics;
    }

    // ---

    AssocModel buildAssociation(Node assocNode) {
        try {
            List<PlayerModel> roleModels = buildRoleModels(assocNode);
            return mf.newAssocModel(
                assocNode.getId(),
                uri(assocNode),
                typeUri(assocNode),
                roleModels.get(0), roleModels.get(1),
                simpleValue(assocNode),
                null    // childTopics=null
            );
        } catch (Exception e) {
            throw new RuntimeException("Building an AssocModel failed (id=" + assocNode.getId() + ", typeUri=" +
                typeUri(assocNode) + ")");
        }
    }

    private List<AssocModel> buildAssociations(Iterable<Node> assocNodes) {
        List<AssocModel> assocs = new ArrayList();
        for (Node assocNode : assocNodes) {
            assocs.add(buildAssociation(assocNode));
        }
        return assocs;
    }

    private List<PlayerModel> buildRoleModels(Node assocNode) {
        List<PlayerModel> roleModels = new ArrayList();
        for (Relationship rel : fetchRelationships(assocNode)) {
            Node node = rel.getEndNode();
            String roleTypeUri = rel.getType().name();
            PlayerModel roleModel = NodeType.of(node).createRoleModel(node, roleTypeUri, mf);
            roleModels.add(roleModel);
        }
        return roleModels;
    }



    // --- DMX -> Neo4j Bridge ---

    private Node storePlayerRelationship(Node assocNode, PlayerModel roleModel) {
        Node playerNode = fetchPlayerNode(roleModel);
        assocNode.createRelationshipTo(
            playerNode,
            getRelationshipType(roleModel.getRoleTypeUri())
        );
        return playerNode;
    }

    private Node fetchPlayerNode(PlayerModel roleModel) {
        if (roleModel instanceof TopicPlayerModel) {
            return fetchTopicPlayerNode((TopicPlayerModel) roleModel);
        } else if (roleModel instanceof AssocPlayerModel) {
            return fetchAssocNode(roleModel.getPlayerId());
        } else {
            throw new RuntimeException("Unexpected role model: " + roleModel);
        }
    }

    private Node fetchTopicPlayerNode(TopicPlayerModel roleModel) {
        if (roleModel.topicIdentifiedByUri()) {
            return fetchTopicNodeByUri(roleModel.getTopicUri());
        } else {
            return fetchTopicNode(roleModel.getPlayerId());
        }
    }



    // --- Neo4j Helper ---

    private Relationship fetchRelationship(Node assocNode, long playerId) {
        List<Relationship> rels = fetchRelationships(assocNode);
        boolean match1 = playerId(rels.get(0)) == playerId;
        boolean match2 = playerId(rels.get(1)) == playerId;
        if (match1 && match2) {
            throw new RuntimeException("Ambiguity: both players have ID " + playerId + " in association " +
                assocNode.getId());
        } else if (match1) {
            return rels.get(0);
        } else if (match2) {
            return rels.get(1);
        } else {
            throw new IllegalArgumentException("ID " + playerId + " is not a player in association " +
                assocNode.getId());
        }
    }

    private List<Relationship> fetchRelationships(Node assocNode) {
        List<Relationship> rels = new ArrayList();
        for (Relationship rel : assocNode.getRelationships(Direction.OUTGOING)) {
            rels.add(rel);
        }
        // sanity check
        if (rels.size() != 2) {
            throw new RuntimeException("Assoc " + assocNode.getId() + " connects " + rels.size() +
                " player instead of 2");
        }
        //
        return rels;
    }

    private long playerId(Relationship rel) {
        return rel.getEndNode().getId();
    }

    // ---

    /**
     * Fetches all associations the given topic or association is involved in.
     *
     * @param   node    a topic node or an association node.
     */
    private List<AssocModel> fetchAssocs(Node node) {
        List<AssocModel> assocs = new ArrayList();
        for (Relationship rel : node.getRelationships(Direction.INCOMING)) {
            Node assocNode = rel.getStartNode();
            // skip non-DM nodes stored by 3rd-party components (e.g. Neo4j Spatial)
            if (!assocNode.hasProperty(KEY_NODE_TYPE)) {
                continue;
            }
            //
            assocs.add(buildAssociation(assocNode));
        }
        return assocs;
    }

    // ---

    private Node fetchTopicNode(long topicId) {
        return checkNodeType(
            fetchNode(topicId), NodeType.TOPIC
        );
    }

    private Node fetchAssocNode(long assocId) {
        return checkNodeType(
            fetchNode(assocId), NodeType.ASSOC
        );
    }

    // ---

    private Node fetchNode(long id) {
        return neo4j.getNodeById(id);
    }

    private Node fetchTopicNodeByUri(String uri) {
        Node node = topicContentExact.get(KEY_URI, uri).getSingle();
        //
        if (node == null) {
            throw new RuntimeException("Topic with URI \"" + uri + "\" not found in DB");
        }
        //
        return checkNodeType(node, NodeType.TOPIC);
    }

    private Node checkNodeType(Node node, NodeType type) {
        if (NodeType.of(node) != type) {
            throw new IllegalArgumentException(type.error(node));
        }
        return node;
    }

    // ---

    private RelationshipType getRelationshipType(String typeName) {
        return relTypeCache.get(typeName);
    }

    // ---

    private String uri(Node node) {
        return (String) node.getProperty(KEY_URI);
    }

    private String typeUri(Node node) {
        return (String) node.getProperty(KEY_TPYE_URI);
    }

    private SimpleValue simpleValue(Node node) {
        return new SimpleValue(node.getProperty(KEY_VALUE));
    }



    // --- DMX Helper ---

    // ### TODO: this is a DB agnostic helper method. It could be moved e.g. to a common base class.
    private List<RelatedTopicModel> buildRelatedTopics(List<AssocModel> assocs, long playerId) {
        List<RelatedTopicModel> relTopics = new ArrayList();
        for (AssocModel assoc : assocs) {
            relTopics.add(mf.newRelatedTopicModel(
                fetchTopic(
                    assoc.getOtherPlayerId(playerId)
                ), assoc)
            );
        }
        return relTopics;
    }

    // ### TODO: this is a DB agnostic helper method. It could be moved e.g. to a common base class.
    private List<RelatedAssocModel> buildRelatedAssocs(List<AssocModel> assocs, long playerId) {
        List<RelatedAssocModel> relAssocs = new ArrayList();
        for (AssocModel assoc : assocs) {
            relAssocs.add(mf.newRelatedAssociationModel(
                fetchAssoc(
                    assoc.getOtherPlayerId(playerId)
                ), assoc)
            );
        }
        return relAssocs;
    }

    // ---

    // ### TODO: a principal copy exists in DMXObjectModel
    private void setDefaults(DMXObjectModel model) {
        if (model.getUri() == null) {
            model.setUri("");
        }
        if (model.getSimpleValue() == null) {
            model.setSimpleValue("");
        }
    }

    /**
     * Checks if a topic or an association with the given URI exists in the DB, and
     * throws an exception if so. If an empty URI ("") is given no check is performed.
     *
     * @param   uri     The URI to check. Must not be null.
     */
    private void checkUriUniqueness(String uri) {
        if (uri.equals("")) {
            return;
        }
        Node n1 = topicContentExact.get(KEY_URI, uri).getSingle();
        Node n2 = assocContentExact.get(KEY_URI, uri).getSingle();
        if (n1 != null || n2 != null) {
            throw new RuntimeException("URI \"" + uri + "\" is not unique");
        }
    }
}
