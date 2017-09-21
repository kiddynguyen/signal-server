package org.whisper.signal.tests.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.whispersystems.dropwizard.simpleauth.AuthValueFactoryProvider;
import org.whisper.signal.controllers.FederationControllerV1;
import org.whisper.signal.controllers.FederationControllerV2;
import org.whisper.signal.controllers.KeysController;
import org.whisper.signal.controllers.MessageController;
import org.whisper.signal.entities.IncomingMessageList;
import org.whisper.signal.entities.MessageProtos;
import org.whisper.signal.entities.PreKeyResponseItem;
import org.whisper.signal.entities.PreKeyResponse;
import org.whisper.signal.entities.SignedPreKey;
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
import java.util.Set;

import io.dropwizard.testing.junit.ResourceTestRule;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;
import static org.whisper.signal.tests.util.JsonHelpers.jsonFixture;

public class FederatedControllerTest {

    private static final String SINGLE_DEVICE_RECIPIENT = "+14151111111";
    private static final String MULTI_DEVICE_RECIPIENT = "+14152222222";

    private PushSender pushSender = mock(PushSender.class);
    private ReceiptSender receiptSender = mock(ReceiptSender.class);
    private FederatedClientManager federatedClientManager = mock(FederatedClientManager.class);
    private AccountsManager accountsManager = mock(AccountsManager.class);
    private MessagesManager messagesManager = mock(MessagesManager.class);
    private RateLimiters rateLimiters = mock(RateLimiters.class);
    private RateLimiter rateLimiter = mock(RateLimiter.class);

    private final SignedPreKey signedPreKey = new SignedPreKey(3333, "foo", "baar");
    private final PreKeyResponse preKeyResponseV2 = new PreKeyResponse("foo", new LinkedList<PreKeyResponseItem>());

    private final ObjectMapper mapper = new ObjectMapper();

    private final MessageController messageController = new MessageController(rateLimiters, pushSender, receiptSender, accountsManager, messagesManager, federatedClientManager);
    private final KeysController keysControllerV2 = mock(KeysController.class);

    @Rule
    public final ResourceTestRule resources = ResourceTestRule.builder()
        .addProvider(AuthHelper.getAuthFilter())
        .addProvider(new AuthValueFactoryProvider.Binder())
        .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
        .addResource(new FederationControllerV1(accountsManager, null, messageController))
        .addResource(new FederationControllerV2(accountsManager, null, messageController, keysControllerV2))
        .build();

    @Before
    public void setup() throws Exception {
        Set<Device> singleDeviceList = new HashSet<Device>() {
            {
                add(new Device(1, null, "foo", "bar", "baz", "isgcm", null, null, false, 111, new SignedPreKey(111, "foo", "bar"), System.currentTimeMillis(), System.currentTimeMillis(), false, false, "Test"));
            }
        };

        Set<Device> multiDeviceList = new HashSet<Device>() {
            {
                add(new Device(1, null, "foo", "bar", "baz", "isgcm", null, null, false, 222, new SignedPreKey(222, "baz", "boop"), System.currentTimeMillis(), System.currentTimeMillis(), false, false, "Test"));
                add(new Device(2, null, "foo", "bar", "baz", "isgcm", null, null, false, 333, new SignedPreKey(333, "rad", "mad"), System.currentTimeMillis(), System.currentTimeMillis(), false, false, "Test"));
            }
        };

        Account singleDeviceAccount = new Account(SINGLE_DEVICE_RECIPIENT, singleDeviceList);
        Account multiDeviceAccount = new Account(MULTI_DEVICE_RECIPIENT, multiDeviceList);

        when(accountsManager.get(eq(SINGLE_DEVICE_RECIPIENT))).thenReturn(Optional.of(singleDeviceAccount));
        when(accountsManager.get(eq(MULTI_DEVICE_RECIPIENT))).thenReturn(Optional.of(multiDeviceAccount));

        when(rateLimiters.getMessagesLimiter()).thenReturn(rateLimiter);

        when(keysControllerV2.getSignedKey(any(Account.class))).thenReturn(Optional.of(signedPreKey));
        when(keysControllerV2.getDeviceKeys(any(Account.class), anyString(), anyString(), any(Optional.class)))
            .thenReturn(Optional.of(preKeyResponseV2));
    }

    @Test
    public void testSingleDeviceCurrent() throws Exception {
        Response response
            = resources.getJerseyTest()
            .target(String.format("/v1/federation/messages/+14152223333/1/%s", SINGLE_DEVICE_RECIPIENT))
            .request()
            .header("Authorization", AuthHelper.getAuthHeader("cyanogen", "foofoo"))
            .put(Entity.entity(mapper.readValue(jsonFixture("fixtures/current_message_single_device.json"), IncomingMessageList.class),
                    MediaType.APPLICATION_JSON_TYPE));

        assertThat("Good Response", response.getStatus(), is(equalTo(204)));

        verify(pushSender).sendMessage(any(Account.class), any(Device.class), any(MessageProtos.Envelope.class), eq(false));
    }

    @Test
    public void testSignedPreKeyV2() throws Exception {
        PreKeyResponse response
            = resources.getJerseyTest()
            .target("/v2/federation/key/+14152223333/1")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader("cyanogen", "foofoo"))
            .get(PreKeyResponse.class);

        assertThat("good response", response.getIdentityKey().equals(preKeyResponseV2.getIdentityKey()));
    }

}
