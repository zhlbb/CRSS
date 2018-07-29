package systems.dmx.core.service.event;

import systems.dmx.core.model.AssociationTypeModel;
import systems.dmx.core.service.EventListener;



public interface PreCreateAssociationTypeListener extends EventListener {

    void preCreateAssociationType(AssociationTypeModel model);
}
