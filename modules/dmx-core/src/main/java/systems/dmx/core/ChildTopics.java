package systems.dmx.core;

import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.TopicModel;

import java.util.List;



public interface ChildTopics extends Iterable<String> {



    // === Accessors ===

    /**
     * Accesses a single-valued child.
     * Throws if there is no such child.
     */
    RelatedTopic getTopic(String compDefUri);

    RelatedTopic getTopicOrNull(String compDefUri);

    /**
     * Accesses a multiple-valued child.
     * Throws if there is no such child. ### TODO: return empty list instead
     */
    List<RelatedTopic> getTopics(String compDefUri);

    List<RelatedTopic> getTopicsOrNull(String compDefUri); // ### TODO: drop this method

    // ---

    Object get(String compDefUri);

    // ---

    ChildTopicsModel getModel();



    // === Convenience Accessors ===

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    String getString(String compDefUri);

    String getStringOrNull(String compDefUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    int getInt(String compDefUri);

    Integer getIntOrNull(String compDefUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    long getLong(String compDefUri);

    Long getLongOrNull(String compDefUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    double getDouble(String compDefUri);

    Double getDoubleOrNull(String compDefUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    boolean getBoolean(String compDefUri);

    Boolean getBooleanOrNull(String compDefUri);

    /**
     * Convenience accessor for the *simple* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    Object getObject(String compDefUri);

    Object getObjectOrNull(String compDefUri);

    // ---

    /**
     * Convenience accessor for the *composite* value of a single-valued child.
     * Throws if the child doesn't exist.
     */
    ChildTopics getChildTopics(String compDefUri);

    // Note: there are no convenience accessors for a multiple-valued child.



    // === Manipulators ===

    // --- Single-valued Children ---

    /**
     * Sets a child.
     */
    ChildTopics set(String compDefUri, TopicModel value);

    // ---

    /**
     * Convenience method to set the simple value of a child.
     *
     * @param   value   The simple value.
     *                  Either String, Integer, Long, Double, or Boolean. Primitive values are auto-boxed.
     */
    ChildTopics set(String compDefUri, Object value);

    /**
     * Convenience method to set the composite value of a child.
     */
    ChildTopics set(String compDefUri, ChildTopicsModel value);

    // ---

    ChildTopics setRef(String compDefUri, long refTopicId);

    ChildTopics setRef(String compDefUri, long refTopicId, ChildTopicsModel relatingAssocChildTopics);

    ChildTopics setRef(String compDefUri, String refTopicUri);

    ChildTopics setRef(String compDefUri, String refTopicUri, ChildTopicsModel relatingAssocChildTopics);

    // ---

    ChildTopics setDeletionRef(String compDefUri, long refTopicId);

    ChildTopics setDeletionRef(String compDefUri, String refTopicUri);

    // --- Multiple-valued Children ---

    ChildTopics add(String compDefUri, TopicModel value);

    // ---

    ChildTopics add(String compDefUri, Object value);

    ChildTopics add(String compDefUri, ChildTopicsModel value);

    // ---

    ChildTopics addRef(String compDefUri, long refTopicId);

    ChildTopics addRef(String compDefUri, long refTopicId, ChildTopicsModel relatingAssocChildTopics);

    ChildTopics addRef(String compDefUri, String refTopicUri);

    ChildTopics addRef(String compDefUri, String refTopicUri, ChildTopicsModel relatingAssocChildTopics);

    // ---

    ChildTopics addDeletionRef(String compDefUri, long refTopicId);

    ChildTopics addDeletionRef(String compDefUri, String refTopicUri);
}
