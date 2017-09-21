package org.whisper.signal.tests.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.whispersystems.dropwizard.simpleauth.AuthValueFactoryProvider;
import org.whisper.signal.controllers.MessageController;
import org.whisper.signal.entities.IncomingMessageList;
import org.whisper.signal.entities.MessageProtos.Envelope;
import org.whisper.signal.entities.MismatchedDevices;
import org.whisper.signal.entities.OutgoingMessageEntity;
import org.whisper.signal.entities.OutgoingMessageEntityList;
import org.whisper.signal.entities.SignedPreKey;
import org.whisper.signal.entities.StaleDevices;
import org.whisper.signal.federation.FederatedClientManager;
import org.whisper.signal.limits.RateLimiter;
import org.whisper.signal.limits.RateLimiters;
import org.whisper.signal.push.PushSender;
import org.whisper.signal.push.ReceiptSender;
import org.whisper.signal.storage.Account;
import org.whisper.signal.storage.AccountsManager;
import org.whisper.signal.storage.Device;
import org.whisper.signal.storage.MessagesManager;
import org.whisper.signal.tests.util.AuthHelper;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.dropwizard.testing.junit.ResourceTestRule;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.whisper.signal.tests.util.JsonHelpers.asJson;
import static org.whisper.signal.tests.util.JsonHelpers.jsonFixture;

public class MessageControllerTest {

    private static final String SINGLE_DEVICE_RECIPIENT = "+14151111111";
    private static final String MULTI_DEVICE_RECIPIENT = "+14152222222";

    private final PushSender pushSender = mock(PushSender.class);
    private final ReceiptSender receiptSender = mock(ReceiptSender.class);
    private final FederatedClientManager federatedClientManager = mock(FederatedClientManager.class);
    private final AccountsManager accountsManager = mock(AccountsManager.class);
    private final MessagesManager messagesManager = mock(MessagesManager.class);
    private final RateLimiters rateLimiters = mock(RateLimiters.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Rule
    public final ResourceTestRule resources = ResourceTestRule.builder()
        .addProvider(AuthHelper.getAuthFilter())
        .addProvider(new AuthValueFactoryProvider.Binder())
        .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
        .addResource(new MessageController(rateLimiters, pushSender, receiptSender, accountsManager,
                messagesManager, federatedClientManager))
        .build();

    @Before
    public void setup() throws Exception {
        Set<Device> singleDeviceList = new HashSet<Device>() {
            {
                add(new Device(1, null, "foo", "bar", "baz", "isgcm", null, null, false, 111, new SignedPreKey(333, "baz", "boop"), System.currentTimeMillis(), System.currentTimeMillis(), false, false, "Test"));
            }
        };

        Set<Device> multiDeviceList = new HashSet<Device>() {
            {
                add(new Device(1, null, "foo", "bar", "baz", "isgcm", null, null, false, 222, new SignedPreKey(111, "foo", "bar"), System.currentTimeMillis(), System.currentTimeMillis(), false, false, "Test"));
                add(new Device(2, null, "foo", "bar", "baz", "isgcm", null, null, false, 333, new SignedPreKey(222, "oof", "rab"), System.currentTimeMillis(), System.currentTimeMillis(), false, false, "Test"));
                add(new Device(3, null, "foo", "bar", "baz", "isgcm", null, null, false, 444, null, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31), System.currentTimeMillis(), false, false, "Test"));
            }
        };

        Account singleDeviceAccount = new Account(SINGLE_DEVICE_RECIPIENT, singleDeviceList);
        Account multiDeviceAccount = new Account(MULTI_DEVICE_RECIPIENT, multiDeviceList);

        when(accountsManager.get(eq(SINGLE_DEVICE_RECIPIENT))).thenReturn(Optional.of(singleDeviceAccount));
        when(accountsManager.get(eq(MULTI_DEVICE_RECIPIENT))).thenReturn(Optional.of(multiDeviceAccount));

        when(rateLimiters.getMessagesLimiter()).thenReturn(rateLimiter);
    }

    @Test
    public synchronized void testSingleDeviceCurrent() throws Exception {
        Response response
            = resources.getJerseyTest()
            .target(String.format("/v1/messages/%s", SINGLE_DEVICE_RECIPIENT))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(mapper.readValue(jsonFixture("fixtures/current_message_single_device.json"), IncomingMessageList.class),
                    MediaType.APPLICATION_JSON_TYPE));

        assertThat("Good Response", response.getStatus(), is(equalTo(200)));

        verify(pushSender, times(1)).sendMessage(any(Account.class), any(Device.class), any(Envelope.class), eq(false));
    }

    @Test
    public synchronized void testMultiDeviceMissing() throws Exception {
        Response response
            = resources.getJerseyTest()
            .target(String.format("/v1/messages/%s", MULTI_DEVICE_RECIPIENT))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(mapper.readValue(jsonFixture("fixtures/current_message_single_device.json"), IncomingMessageList.class),
                    MediaType.APPLICATION_JSON_TYPE));

        assertThat("Good Response Code", response.getStatus(), is(equalTo(409)));

        assertThat("Good Response Body",
            asJson(response.readEntity(MismatchedDevices.class)),
            is(equalTo(jsonFixture("fixtures/missing_device_response.json"))));

        verifyNoMoreInteractions(pushSender);
    }

    @Test
    public synchronized void testMultiDeviceExtra() throws Exception {
        Response response
            = resources.getJerseyTest()
            .target(String.format("/v1/messages/%s", MULTI_DEVICE_RECIPIENT))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(mapper.readValue(jsonFixture("fixtures/current_message_extra_device.json"), IncomingMessageList.class),
                    MediaType.APPLICATION_JSON_TYPE));

        assertThat("Good Response Code", response.getStatus(), is(equalTo(409)));

        assertThat("Good Response Body",
            asJson(response.readEntity(MismatchedDevices.class)),
            is(equalTo(jsonFixture("fixtures/missing_device_response2.json"))));

        verifyNoMoreInteractions(pushSender);
    }

    @Test
    public synchronized void testMultiDevice() throws Exception {
        Response response
            = resources.getJerseyTest()
            .target(String.format("/v1/messages/%s", MULTI_DEVICE_RECIPIENT))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(mapper.readValue(jsonFixture("fixtures/current_message_multi_device.json"), IncomingMessageList.class),
                    MediaType.APPLICATION_JSON_TYPE));

        assertThat("Good Response Code", response.getStatus(), is(equalTo(200)));

        verify(pushSender, times(2)).sendMessage(any(Account.class), any(Device.class), any(Envelope.class), eq(false));
    }

    @Test
    public synchronized void testRegistrationIdMismatch() throws Exception {
        Response response
            = resources.getJerseyTest().target(String.format("/v1/messages/%s", MULTI_DEVICE_RECIPIENT))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(mapper.readValue(jsonFixture("fixtures/current_message_registration_id.json"), IncomingMessageList.class),
                    MediaType.APPLICATION_JSON_TYPE));

        assertThat("Good Response Code", response.getStatus(), is(equalTo(410)));

        assertThat("Good Response Body",
            asJson(response.readEntity(StaleDevices.class)),
            is(equalTo(jsonFixture("fixtures/mismatched_registration_id.json"))));

        verifyNoMoreInteractions(pushSender);

    }

    @Test
    public synchronized void testGetMessages() throws Exception {

        final long timestampOne = 313377;
        final long timestampTwo = 313388;

        List<OutgoingMessageEntity> messages = new LinkedList<OutgoingMessageEntity>() {
            {
                add(new OutgoingMessageEntity(1L, Envelope.Type.CIPHERTEXT_VALUE, null, timestampOne, "+14152222222", 2, "hi there".getBytes(), null));
                add(new OutgoingMessageEntity(2L, Envelope.Type.RECEIPT_VALUE, null, timestampTwo, "+14152222222", 2, null, null));
            }
        };

        OutgoingMessageEntityList messagesList = new OutgoingMessageEntityList(messages, false);

        when(messagesManager.getMessagesForDevice(eq(AuthHelper.VALID_NUMBER), eq(1L))).thenReturn(messagesList);

        OutgoingMessageEntityList response
            = resources.getJerseyTest().target("/v1/messages/")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .accept(MediaType.APPLICATION_JSON_TYPE)
            .get(OutgoingMessageEntityList.class);

        assertEquals(response.getMessages().size(), 2);

        assertEquals(response.getMessages().get(0).getId(), 0);
        assertEquals(response.getMessages().get(1).getId(), 0);

        assertEquals(response.getMessages().get(0).getTimestamp(), timestampOne);
        assertEquals(response.getMessages().get(1).getTimestamp(), timestampTwo);
    }

    @Test
    public synchronized void testGetMessagesBadAuth() throws Exception {
        final long timestampOne = 313377;
        final long timestampTwo = 313388;

        List<OutgoingMessageEntity> messages = new LinkedList<OutgoingMessageEntity>() {
            {
                add(new OutgoingMessageEntity(1L, Envelope.Type.CIPHERTEXT_VALUE, null, timestampOne, "+14152222222", 2, "hi there".getBytes(), null));
                add(new OutgoingMessageEntity(2L, Envelope.Type.RECEIPT_VALUE, null, timestampTwo, "+14152222222", 2, null, null));
            }
        };

        OutgoingMessageEntityList messagesList = new OutgoingMessageEntityList(messages, false);

        when(messagesManager.getMessagesForDevice(eq(AuthHelper.VALID_NUMBER), eq(1L))).thenReturn(messagesList);

        Response response
            = resources.getJerseyTest().target("/v1/messages/")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.INVALID_PASSWORD))
            .accept(MediaType.APPLICATION_JSON_TYPE)
            .get();

        assertThat("Unauthorized response", response.getStatus(), is(equalTo(401)));
    }

    @Test
    public synchronized void testDeleteMessages() throws Exception {
        long timestamp = System.currentTimeMillis();
        when(messagesManager.delete(AuthHelper.VALID_NUMBER, 1, "+14152222222", 31337))
            .thenReturn(Optional.of(new OutgoingMessageEntity(31337L,
                        Envelope.Type.CIPHERTEXT_VALUE,
                        null, timestamp,
                        "+14152222222", 1, "hi".getBytes(), null)));

        when(messagesManager.delete(AuthHelper.VALID_NUMBER, 1, "+14152222222", 31338))
            .thenReturn(Optional.of(new OutgoingMessageEntity(31337L,
                        Envelope.Type.RECEIPT_VALUE,
                        null, System.currentTimeMillis(),
                        "+14152222222", 1, null, null)));

        when(messagesManager.delete(AuthHelper.VALID_NUMBER, 1, "+14152222222", 31339))
            .thenReturn(Optional.<OutgoingMessageEntity>absent());

        Response response = resources.getJerseyTest()
            .target(String.format("/v1/messages/%s/%d", "+14152222222", 31337))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .delete();

        assertThat("Good Response Code", response.getStatus(), is(equalTo(204)));
        verify(receiptSender).sendReceipt(any(Account.class), eq("+14152222222"), eq(timestamp), eq(Optional.<String>absent()));

        response = resources.getJerseyTest()
            .target(String.format("/v1/messages/%s/%d", "+14152222222", 31338))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .delete();

        assertThat("Good Response Code", response.getStatus(), is(equalTo(204)));
        verifyNoMoreInteractions(receiptSender);

        response = resources.getJerseyTest()
            .target(String.format("/v1/messages/%s/%d", "+14152222222", 31339))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
            .delete();

        assertThat("Good Response Code", response.getStatus(), is(equalTo(204)));
        verifyNoMoreInteractions(receiptSender);

    }

}
