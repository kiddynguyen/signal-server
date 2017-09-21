package org.whisper.dispatch.io;

import org.whisper.dispatch.redis.PubSubConnection;

public interface RedisPubSubConnectionFactory {

    public PubSubConnection connect();

}
