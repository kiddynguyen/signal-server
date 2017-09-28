package org.whisper.signal.dispatch.io;

import org.whisper.signal.dispatch.redis.PubSubConnection;

public interface RedisPubSubConnectionFactory {

    public PubSubConnection connect();

}
