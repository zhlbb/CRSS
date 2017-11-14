package de.deepamehta.core.model;

import org.codehaus.jettison.json.JSONArray;



/**
 * A container for config topics.
 * <p>
 * Config topics can be accessed by their type URI.
 * A view config can contain only one config topic with a certain type URI.
 *
 * @author <a href="mailto:jri@deepamehta.de">Jörg Richter</a>
 */
public interface ViewConfigurationModel {

    Iterable<? extends TopicModel> getConfigTopics();

    /**
     * @return  the config topic for the given type URI, or <code>null</code> if there is none.
     */
    TopicModel getConfigTopic(String configTypeUri);

    /**
     * Adds a config topic to this view config.
     *
     * @throws  RuntimeException    if this view config already contains a config topic with the same type URI.
     */
    void addConfigTopic(TopicModel configTopic);

    /**
     * Sets a single value of a certain config topic.
     * If no such config topic exists in this view config it is created.
     *
     * @param   configTypeUri   The type URI of the config topic, e.g. "dm4.webclient.view_config"
     * @param   childTypeUri    The child type URI of the config value to set, e.g. "dm4.webclient.icon"
     * @param   value           The config value (String, Integer, Long, Double, or Boolean)
     */
    ViewConfigurationModel setConfigValue(String configTypeUri, String childTypeUri, Object value);

    ViewConfigurationModel setConfigValueRef(String configTypeUri, String childTypeUri, Object topicIdOrUri);

    // ### TODO: drop method?
    void updateConfigTopic(TopicModel configTopic);

    // ---

    /**
     * ### TODO: drop method?
     *
     * Lookup a view config value.
     * <p>
     * Compare to client-side counterpart: function get_view_config() in webclient.js
     *
     * @param   configTypeUri   The type URI of the config topic, e.g. "dm4.webclient.view_config"
     * @param   childTypeUri    The child type URI of the config value to lookup, e.g. "dm4.webclient.icon"
     *
     * @return  The config value, or <code>null</code> if no value is set
     */
    Object getConfigValue(String configTypeUri, String childTypeUri);

    // ---

    JSONArray toJSONArray();
}
