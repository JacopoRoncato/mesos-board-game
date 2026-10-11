package mesos.network.server;

import java.io.Serializable;

/**
 * Marker interface for all messages sent from server to client.
 * Concrete subclasses: OkMessage, ErrMessage, SnapshotMessage.
 */
public interface ServerMessage extends Serializable { }