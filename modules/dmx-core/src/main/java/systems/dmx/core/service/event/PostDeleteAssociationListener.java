package systems.dmx.core.service.event;

import systems.dmx.core.model.AssocModel;
import systems.dmx.core.service.EventListener;



public interface PostDeleteAssociationListener extends EventListener {

    void postDeleteAssociation(AssocModel assoc);
}
