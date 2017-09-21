package org.whisper.signal.websocket;

import org.whisper.signal.storage.PubSubAddress;
import org.whisper.signal.util.Util;

public class WebSocketConnectionInfo implements PubSubAddress {

    private final WebsocketAddress address;

    public WebSocketConnectionInfo(WebsocketAddress address) {
        this.address = address;
    }

    public WebSocketConnectionInfo(String serialized) throws FormattingException {
        String[] parts = serialized.split("[:]", 3);

        if (parts.length != 3 || !"c".equals(parts[2])) {
            throw new FormattingException("Bad address: " + serialized);
        }

        try {
            this.address = new WebsocketAddress(parts[0], Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw new FormattingException(e);
        }
    }

    public String serialize() {
        return address.serialize() + ":c";
    }

    public WebsocketAddress getWebsocketAddress() {
        return address;
    }

    public static boolean isType(String address) {
        return address.endsWith(":c");
    }

    @Override
    public boolean equals(Object other) {
        return other != null
            && other instanceof WebSocketConnectionInfo
            && ((WebSocketConnectionInfo) other).address.equals(address);
    }

    @Override
    public int hashCode() {
        return Util.hashCode(address, "c");
    }

    public static class FormattingException extends Exception {

        public FormattingException(String message) {
            super(message);
        }

        public FormattingException(Exception e) {
            super(e);
        }
    }
}
