package de.deepamehta.core.impl;

import de.deepamehta.core.model.AssociationRoleModel;
import de.deepamehta.core.model.RoleModel;

import org.codehaus.jettison.json.JSONObject;



class AssociationRoleModelImpl extends RoleModelImpl implements AssociationRoleModel {

    // ---------------------------------------------------------------------------------------------------- Constructors

    AssociationRoleModelImpl(long assocId, String roleTypeUri) {
        super(assocId, roleTypeUri);
    }

    AssociationRoleModelImpl(JSONObject assocRoleModel) {
        try {
            this.playerId = assocRoleModel.getLong("assoc_id");
            this.roleTypeUri = assocRoleModel.getString("role_type_uri");
        } catch (Exception e) {
            throw new RuntimeException("Parsing AssociationRoleModel failed (JSONObject=" + assocRoleModel + ")", e);
        }
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    // === Implementation of abstract RoleModel methods ===

    @Override
    public boolean refsSameObject(RoleModel model) {
        if (model instanceof AssociationRoleModel) {
            AssociationRoleModel assocRole = (AssociationRoleModel) model;
            return assocRole.playerId == playerId;
        }
        return false;
    }

    @Override
    public JSONObject toJSON() {
        try {
            JSONObject o = new JSONObject();
            o.put("assoc_id", playerId);
            o.put("role_type_uri", roleTypeUri);
            return o;
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed (" + this + ")", e);
        }
    }

    // === Java API ===

    @Override
    public String toString() {
        return "\n        association role (roleTypeUri=\"" + roleTypeUri + "\", playerId=" + playerId + ")";
    }
}
