package de.deepamehta.plugins.workspaces.migrations;

import de.deepamehta.plugins.config.ConfigService;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.core.service.ResultList;

import java.util.logging.Logger;



/**
 * Adds a "Enabled Sharing Modes" config topic to each username.
 * Runs only in UPDATE mode.
 * <p>
 * Note: when CLEAN_INSTALLing the admin user already got its config topics
 * as the Config service is already in charge.
 * <p>
 * Part of DM 4.8-SNAPSHOT
 */
public class Migration8 extends Migration {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    @Inject
    private ConfigService configService;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public void run() {
        ResultList<RelatedTopic> usernames = dms.getTopics("dm4.accesscontrol.username", 0);
        logger.info("########## Adding \"dm4.workspaces.enabled_sharing_modes\" config topic to " +
            usernames.getSize() + " usernames");
        for (Topic username : usernames) {
            configService.createConfigTopic("dm4.workspaces.enabled_sharing_modes", username);
        }
    }
}
