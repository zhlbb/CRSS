package systems.dmx.core.impl;

import systems.dmx.core.model.RelatedAssocModel;



public class RelatedAssocModelImpl extends AssocModelImpl implements RelatedAssocModel {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private AssocModelImpl relatingAssoc;

    // ---------------------------------------------------------------------------------------------------- Constructors

    RelatedAssocModelImpl(AssocModelImpl assoc, AssocModelImpl relatingAssoc) {
        super(assoc);
        this.relatingAssoc = relatingAssoc;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public AssocModelImpl getRelatingAssoc() {
        return relatingAssoc;
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    @Override
    String className() {
        return "related association";
    }

    @Override
    RelatedAssocImpl instantiate() {
        return new RelatedAssocImpl(this, al);
    }
}
