package com.sportsify.chat.infrastructure.webSocket;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.security.Principal;

public class PrincipalWebSocketSession extends WebSocketSessionDecorator {

    private volatile Principal principal;

    public PrincipalWebSocketSession(WebSocketSession delegate) {
        super(delegate);
        this.principal = delegate.getPrincipal();
    }

    public void setPrincipal(Principal principal) {
        this.principal = principal;
    }

    @Override
    public Principal getPrincipal() {
        return this.principal;
    }
}
