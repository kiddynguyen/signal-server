package org.whisper.signal.tests.push;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.whisper.signal.push.APNSender;
import org.whisper.signal.push.ApnFallbackManager;
import org.whisper.signal.push.ApnFallbackManager.ApnFallbackTask;
import org.whisper.signal.push.ApnMessage;
import org.whisper.signal.storage.PubSubManager;
import org.whisper.signal.storage.PubSubProtos;
import org.whisper.signal.util.Util;
import org.whisper.signal.websocket.WebSocketConnectionInfo;
import org.whisper.signal.websocket.WebsocketAddress;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ApnFallbackManagerTest {

    @Test
    public void testFullFallback() throws Exception {
        APNSender apnSender = mock(APNSender.class);
        PubSubManager pubSubManager = mock(PubSubManager.class);
        WebsocketAddress address = new WebsocketAddress("+14152222223", 1L);
        WebSocketConnectionInfo info = new WebSocketConnectionInfo(address);
        ApnMessage message = new ApnMessage("bar", "123", 1, "hmm", true, 1111);
        ApnFallbackTask task = new ApnFallbackTask("foo", "voipfoo", message, 500, 0);

        ApnFallbackManager apnFallbackManager = new ApnFallbackManager(apnSender, pubSubManager);
        apnFallbackManager.start();

        apnFallbackManager.schedule(address, task);

        Util.sleep(1100);

        ArgumentCaptor<ApnMessage> captor = ArgumentCaptor.forClass(ApnMessage.class);
        verify(apnSender, times(2)).sendMessage(captor.capture());
        verify(pubSubManager).unsubscribe(eq(info), eq(apnFallbackManager));

        List<ApnMessage> arguments = captor.getAllValues();

        assertEquals(arguments.get(0).getMessage(), message.getMessage());
        assertEquals(arguments.get(0).getApnId(), task.getVoipApnId());
//    assertEquals(arguments.get(0).getExpirationTime(), Integer.MAX_VALUE * 1000L);

        assertEquals(arguments.get(1).getMessage(), message.getMessage());
        assertEquals(arguments.get(1).getApnId(), task.getApnId());
        assertEquals(arguments.get(1).getExpirationTime(), Integer.MAX_VALUE * 1000L);
    }

    @Test
    public void testNoFallback() throws Exception {
        APNSender pushServiceClient = mock(APNSender.class);
        PubSubManager pubSubManager = mock(PubSubManager.class);
        WebsocketAddress address = new WebsocketAddress("+14152222222", 1);
        WebSocketConnectionInfo info = new WebSocketConnectionInfo(address);
        ApnMessage message = new ApnMessage("bar", "123", 1, "hmm", true, 5555);
        ApnFallbackTask task = new ApnFallbackTask("foo", "voipfoo", message, 500, 0);

        ApnFallbackManager apnFallbackManager = new ApnFallbackManager(pushServiceClient, pubSubManager);
        apnFallbackManager.start();

        apnFallbackManager.schedule(address, task);
        apnFallbackManager.onDispatchMessage(info.serialize(),
            PubSubProtos.PubSubMessage.newBuilder()
            .setType(PubSubProtos.PubSubMessage.Type.CONNECTED)
            .build().toByteArray());

        verify(pubSubManager).unsubscribe(eq(info), eq(apnFallbackManager));

        Util.sleep(1100);

        verifyNoMoreInteractions(pushServiceClient);
    }

}
