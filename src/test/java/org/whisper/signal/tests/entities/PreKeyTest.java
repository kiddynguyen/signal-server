package org.whisper.signal.tests.entities;

import org.junit.Test;
import org.whisper.signal.entities.ClientContact;
import org.whisper.signal.entities.PreKey;
import org.whisper.signal.util.Util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.whisper.signal.tests.util.JsonHelpers.*;

public class PreKeyTest {

    @Test
    public void deserializeFromJSONV() throws Exception {
        ClientContact contact = new ClientContact(Util.getContactToken("+14152222222"),
            "whisper", false, false);

        assertThat("a ClientContact can be deserialized from JSON",
            fromJson(jsonFixture("fixtures/contact.relay.json"), ClientContact.class),
            is(contact));
    }

    @Test
    public void serializeToJSONV2() throws Exception {
        PreKey preKey = new PreKey(1234, "test");

        assertThat("PreKeyV2 Serialization works",
            asJson(preKey),
            is(equalTo(jsonFixture("fixtures/prekey_v2.json"))));
    }

}
