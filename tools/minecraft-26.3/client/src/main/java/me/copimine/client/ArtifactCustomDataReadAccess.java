package me.copimine.client;

/** Read-only string access to known keys in an immutable CustomData component. */
public interface ArtifactCustomDataReadAccess {
    String getTopLevelString(String key);

    String getNestedString(String compoundKey, String key);
}
