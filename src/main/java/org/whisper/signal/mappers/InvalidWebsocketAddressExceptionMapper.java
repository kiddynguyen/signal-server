package org.whisper.signal.mappers;

import org.whisper.signal.websocket.InvalidWebsocketAddressException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class InvalidWebsocketAddressExceptionMapper implements ExceptionMapper<InvalidWebsocketAddressException> {

    @Override
    public Response toResponse(InvalidWebsocketAddressException exception) {
        return Response.status(Response.Status.BAD_REQUEST).build();
    }
}
