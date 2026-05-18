package com.sportsify.chat.infrastructure.webSocket;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.security.Principal;
import java.util.Objects;

public class PrincipalWebSocketSession extends WebSocketSessionDecorator {

    private volatile Principal principal;

    public PrincipalWebSocketSession(WebSocketSession delegate) {
        super(delegate);
        this.principal = delegate.getPrincipal();
    }

    @Override
    public Principal getPrincipal() {
        return this.principal;
    }

    public void setPrincipal(Principal principal) {
        Objects.requireNonNull(principal, "principal");
        this.principal = principal;
    }
}
