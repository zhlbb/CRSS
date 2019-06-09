package systems.dmx.core.impl;

import systems.dmx.core.model.AssocModel;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.DMXObjectModel;
import systems.dmx.core.model.PlayerModel;
import systems.dmx.core.model.TopicPlayerModel;
import systems.dmx.core.model.TypeModel;
import systems.dmx.core.service.DMXEvent;
import systems.dmx.core.service.Directive;
import systems.dmx.core.util.DMXUtils;

import org.codehaus.jettison.json.JSONObject;

import java.util.List;



/**
 * Collection of the data that makes up an {@link Assoc}.
 *
 * @author <a href="mailto:jri@deepamehta.de">Jörg Richter</a>
 */
class AssocModelImpl extends DMXObjectModelImpl implements AssocModel {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    PlayerModelImpl player1;    // may be null in update models
    PlayerModelImpl player2;    // may be null in update models

    // ---------------------------------------------------------------------------------------------------- Constructors

    AssocModelImpl(DMXObjectModelImpl object, PlayerModelImpl player1, PlayerModelImpl player2) {
        super(object);
        this.player1 = player1;
        this.player2 = player2;
    }

    AssocModelImpl(AssocModelImpl assoc) {
        super(assoc);
        this.player1 = assoc.player1;
        this.player2 = assoc.player2;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public PlayerModelImpl getPlayer1() {
        return player1;
    }

    @Override
    public PlayerModelImpl getPlayer2() {
        return player2;
    }

    // ---

    @Override
    public void setPlayer1(PlayerModel player1) {
        this.player1 = (PlayerModelImpl) player1;
    }

    @Override
    public void setPlayer2(PlayerModel player2) {
        this.player2 = (PlayerModelImpl) player2;
    }

    // --- Convenience Methods ---

    @Override
    public PlayerModelImpl getRoleModel(String roleTypeUri) {
        boolean rm1 = player1.getRoleTypeUri().equals(roleTypeUri);
        boolean rm2 = player2.getRoleTypeUri().equals(roleTypeUri);
        if (rm1 && rm2) {
            throw new RuntimeException("Ambiguous getRoleModel() call: both players play role \"" + roleTypeUri +
                "\" (" + this + ")");
        }
        return rm1 ? player1 : rm2 ? player2 : null;
    }

    @Override
    public boolean hasSameRoleTypeUris() {
        return player1.getRoleTypeUri().equals(player2.getRoleTypeUri());
    }

    @Override
    public boolean matches(String roleTypeUri1, long playerId1, String roleTypeUri2, long playerId2) {
        if (roleTypeUri1.equals(roleTypeUri2)) {
            throw new IllegalArgumentException("matches() was called with 2 identical role type URIs (\"" +
                roleTypeUri1 + "\")");
        }
        if (!hasSameRoleTypeUris()) {
            PlayerModel r1 = getRoleModel(roleTypeUri1);
            PlayerModel r2 = getRoleModel(roleTypeUri2);
            if (r1 != null && r1.getId() == playerId1 &&
                r2 != null && r2.getId() == playerId2) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long getOtherPlayerId(long id) {
        try {
            long id1 = player1.getId();
            long id2 = player2.getId();
            if (id1 == id) {
                return id2;
            } else if (id2 == id) {
                return id1;
            } else {
                throw new IllegalArgumentException(id + " is not a player in " + this);
            }
        } catch (Exception e) {
            throw new RuntimeException("Can't get other player of " + id + " in " + this, e);
        }
    }



    // === Implementation of the abstract methods ===

    @Override
    public PlayerModel createRoleModel(String roleTypeUri) {
        return mf.newAssocRoleModel(id, roleTypeUri);
    }



    // === Serialization ===

    @Override
    public JSONObject toJSON() {
        try {
            return super.toJSON()
                .put("player1", player1 != null ? player1.toJSON() : null)
                .put("player2", player2 != null ? player2.toJSON() : null);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }



    // === Java API ===

    @Override
    public AssocModel clone() {
        try {
            AssocModel model = (AssocModel) super.clone();
            model.setPlayer1(player1.clone());
            model.setPlayer2(player2.clone());
            return model;
        } catch (Exception e) {
            throw new RuntimeException("Cloning an AssocModel failed", e);
        }
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    @Override
    String className() {
        return "association";
    }

    @Override
    AssocImpl instantiate() {
        return new AssocImpl(this, pl);
    }

    @Override
    AssocModelImpl createModelWithChildTopics(ChildTopicsModel childTopics) {
        return mf.newAssocModel(typeUri, childTopics);
    }

    // ---

    @Override
    final AssocTypeModelImpl getType() {
        return pl.typeStorage.getAssocType(typeUri);
    }

    @Override
    final List<AssocModelImpl> getAssocs() {
        return pl.fetchAssocAssocs(id);
    }

    // ---

    @Override
    final RelatedTopicModelImpl getRelatedTopic(String assocTypeUri, String myRoleTypeUri, String othersRoleTypeUri,
                                                                                           String othersTopicTypeUri) {
        return pl.fetchAssocRelatedTopic(id, assocTypeUri, myRoleTypeUri, othersRoleTypeUri, othersTopicTypeUri);
    }

    @Override
    final List<RelatedTopicModelImpl> getRelatedTopics(String assocTypeUri, String myRoleTypeUri,
                                                                                           String othersRoleTypeUri,
                                                                                           String othersTopicTypeUri) {
        return pl.fetchAssocRelatedTopics(id, assocTypeUri, myRoleTypeUri, othersRoleTypeUri, othersTopicTypeUri);
    }

    // ---

    @Override
    final void storeUri() {
        pl.storeAssocUri(id, uri);
    }

    @Override
    final void storeTypeUri() {
        reassignInstantiation();
        pl.storeAssocTypeUri(id, typeUri);
    }

    @Override
    final void storeSimpleValue() {
        pl.storeAssocValue(id, value, typeUri, isHtml());
    }

    @Override
    final void storeProperty(String propUri, Object propValue, boolean addToIndex) {
        pl.storeAssocProperty(id, propUri, propValue, addToIndex);
    }

    @Override
    final void removeProperty(String propUri) {
        pl.removeAssocProperty(id, propUri);
    }

    // ---

    @Override
    final void _delete() {
        pl._deleteAssoc(id);
    }

    // ---

    @Override
    final void checkReadAccess() {
        pl.checkAssocReadAccess(id);
    }

    @Override
    final void checkWriteAccess() {
        pl.checkAssocWriteAccess(id);
    }

    // ---

    @Override
    final DMXEvent getPreUpdateEvent() {
        return CoreEvent.PRE_UPDATE_ASSOCIATION;
    }

    @Override
    final DMXEvent getPostUpdateEvent() {
        return CoreEvent.POST_UPDATE_ASSOCIATION;
    }

    @Override
    final DMXEvent getPreDeleteEvent() {
        return CoreEvent.PRE_DELETE_ASSOCIATION;
    }

    @Override
    final DMXEvent getPostDeleteEvent() {
        return CoreEvent.POST_DELETE_ASSOCIATION;
    }

    // ---

    @Override
    final Directive getUpdateDirective() {
        return Directive.UPDATE_ASSOCIATION;
    }

    @Override
    final Directive getDeleteDirective() {
        return Directive.DELETE_ASSOCIATION;
    }



    // === Core Internal Hooks ===

    @Override
    void preCreate() {
        // Note: auto-typing only works for generic assocs (of type "Association") and for by-ID players.
        // That's why auto-typing does not interfere with comp defs created programmatically (through migration).
        if (DMXUtils.associationAutoTyping(this, "dmx.core.topic_type", "dmx.core.topic_type",
                "dmx.core.composition_def", "dmx.core.child_type", "dmx.core.parent_type") != null ||
            DMXUtils.associationAutoTyping(this, "dmx.core.topic_type", "dmx.core.assoc_type",
                "dmx.core.composition_def", "dmx.core.child_type", "dmx.core.parent_type") != null) {
            childTopics.putRef("dmx.core.cardinality", "dmx.core.one");
        }
        //
        duplicateCheck();
    }

    @Override
    void postCreate() {
        if (isCompDef(this)) {
            createCompDef();
        }
    }

    @Override
    void postUpdate(DMXObjectModel updateModel, DMXObjectModel oldObject) {
        // update association specific parts: the 2 players
        updatePlayers((AssocModel) updateModel);
        //
        duplicateCheck();
        //
        // Type Editor Support
        if (isCompDef(this)) {
            if (isCompDef((AssocModel) oldObject)) {
                updateCompDef((AssocModel) oldObject);
            } else {
                createCompDef();
            }
        } else if (isCompDef((AssocModel) oldObject)) {
            removeCompDef();
        }
    }

    @Override
    void preDelete() {
        // Type Editor Support
        if (isCompDef(this)) {
            // Note: we listen to the PRE event here, not the POST event. At POST time the compdef sequence might be
            // interrupted, which would result in a corrupted sequence once rebuild. (Due to the interruption, while
            // rebuilding not all segments would be catched for deletion and recreated redundantly -> ambiguity.)
            // ### FIXDOC
            removeCompDef();
        }
    }



    // ===

    /**
     * @return  this association's player which plays the given role.
     *          If there is no such player, null is returned.
     *          <p>
     *          If there are 2 such players an exception is thrown.
     */
    DMXObjectModelImpl getDMXObjectByRole(String roleTypeUri) {
        PlayerModelImpl player = getRoleModel(roleTypeUri);
        return player != null ? player.getDMXObject(this) : null;
    }

    DMXObjectModelImpl getDMXObjectByType(String typeUri) {
        DMXObjectModelImpl object1 = filter(player1, typeUri);
        DMXObjectModelImpl object2 = filter(player2, typeUri);
        if (object1 != null && object2 != null) {
            throw new RuntimeException("Ambiguous getDMXObjectByType() call: both players are of type \"" + typeUri +
                "\" (" + this + ")");
        }
        return object1 != null ? object1 : object2 != null ? object2 : null;
    }

    // ---

    void updateRoleTypeUri(PlayerModelImpl player, String roleTypeUri) {
        player.setRoleTypeUri(roleTypeUri);                         // update memory
        pl.storeRoleTypeUri(id, player.id, player.roleTypeUri);     // update DB
    }

    // ------------------------------------------------------------------------------------------------- Private Methods



    private void duplicateCheck() {
        // ### FIXME: the duplicate check is supported only for topic players, and if they are identified by-ID.
        // Note: we can't call roleModel.getDMXObject() as this would build an entire object model, but its "value"
        // is not yet available in case this association is part of the player's composite structure.
        // Compare to DMXUtils.getRoleModels()
        if (!(player1 instanceof TopicPlayerModel) || !(player2 instanceof TopicPlayerModel)) {
            return;
        }
        // Note: only readable assocs (access control) are considered
        for (AssocModelImpl assoc : pl._getAssocs(typeUri, player1.id, player2.id, player1.roleTypeUri,
                                                                                   player2.roleTypeUri)) {
            if (assoc.id != id && assoc.value.equals(value)) {
                throw new RuntimeException("Duplicate: such an association exists already (ID=" + assoc.id +
                    ", typeUri=\"" + typeUri + "\", value=\"" + value + "\")");
            }
        }
    }



    // === Update (memory + DB) ===

    /**
     * @param   updateModel     The data to update.
     *                          If player 1 is <code>null</code> it is not updated.
     *                          If player 2 is <code>null</code> it is not updated.
     */
    private void updatePlayers(AssocModel updateModel) {
        updatePlayer(updateModel.getPlayer1(), 1);
        updatePlayer(updateModel.getPlayer2(), 2);
    }

    /**
     * @param   nr      used only for logging
     */
    private void updatePlayer(PlayerModel updateModel, int nr) {
        try {
            if (updateModel != null) {     // abort if no update is requested
                // Note: We must lookup the roles individually.
                // The player order (getPlayer1(), getPlayer2()) is undeterministic and not fix.
                PlayerModelImpl player = getRole(updateModel);
                String newRoleTypeUri = updateModel.getRoleTypeUri();   // new value
                String roleTypeUri = player.getRoleTypeUri();           // current value
                if (!roleTypeUri.equals(newRoleTypeUri)) {              // has changed?
                    logger.info("### Changing role type " + nr + " of association " + id + ": \"" + roleTypeUri +
                        "\" -> \"" + newRoleTypeUri + "\"");
                    updateRoleTypeUri(player, newRoleTypeUri);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Updating player " + nr + " of association " + id + " failed, updateModel=" +
                updateModel, e);
        }
    }



    // === Roles (memory access) ===

    /**
     * Returns this association's player which refers to the same object as the given player.
     * The player returned is found by comparing topic IDs, topic URIs, or association IDs. ### FIXDOC
     * The role types are <i>not</i> compared.
     * <p>
     * If the object refered by the given player is not a player in this association an exception is thrown.
     */
    private PlayerModelImpl getRole(PlayerModel roleModel) {
        if (player1.refsSameObject(roleModel)) {
            return player1;
        } else if (player2.refsSameObject(roleModel)) {
            return player2;
        }
        throw new RuntimeException(roleModel + " is not part of " + this);
    }

    // ---

    private DMXObjectModelImpl filter(PlayerModelImpl player, String typeUri) {
        DMXObjectModelImpl object = player.getDMXObject(this);
        return object.getTypeUri().equals(typeUri) ? object : null;
    }



    // === Store (DB only) ===

    private void reassignInstantiation() {
        // remove current assignment
        fetchInstantiation().delete();
        // create new assignment
        pl.createAssocInstantiation(id, typeUri);
    }

    private AssocModelImpl fetchInstantiation() {
        RelatedTopicModelImpl assocType = getRelatedTopic("dmx.core.instantiation", "dmx.core.instance",
            "dmx.core.type", "dmx.core.assoc_type");
        //
        if (assocType == null) {
            throw new RuntimeException("Assoc " + id + " is not associated to an association type");
        }
        //
        return assocType.getRelatingAssoc();
    }



    // === Type Editor Support ===

    // 3 methods to bridge between assoc and comp def

    /**
     * Creates an comp def model based on this assoc model, and 1) puts it in the type cache, and 2) updates the
     * DB sequence, and 3) adds an UPDATE_TYPE directive.
     *
     * Preconditions:
     *   - The association is stored in DB
     */
    private void createCompDef() {
        TypeModelImpl parentType = fetchParentType();
        logger.info("##### Adding comp def " + id + " to type \"" + parentType.getUri() + "\"");
        //
        parentType._addCompDef(this);
    }

    private void updateCompDef(AssocModel oldAssoc) {
        TypeModelImpl parentType = fetchParentType();
        logger.info("##### Updating comp def " + id + " of type \"" + parentType.getUri() + "\"");
        //
        parentType._updateCompDef(this, oldAssoc);
    }

    private void removeCompDef() {
        TypeModelImpl parentType = fetchParentType();
        logger.info("##### Removing comp def " + id + " from type \"" + parentType.getUri() + "\"");
        //
        parentType._removeCompDefFromMemoryAndRebuildSequence(this);
    }

    // ---

    private boolean isCompDef(AssocModel assoc) {
        String typeUri = assoc.getTypeUri();
        if (!typeUri.equals("dmx.core.composition_def")) {
            return false;
        }
        //
        if (assoc.hasSameRoleTypeUris()) {
            return false;
        }
        if (assoc.getRoleModel("dmx.core.parent_type") == null ||
            assoc.getRoleModel("dmx.core.child_type") == null)  {
            return false;
        }
        //
        return true;
    }

    private TypeModelImpl fetchParentType() {
        return pl.typeStorage.fetchParentType(this);
    }
}
