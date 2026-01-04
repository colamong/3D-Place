package com.colombus.ws.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.colombus.ws.handler.PaintWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final PaintWebSocketHandler paintWebSocketHandler;

	public WebSocketConfig(PaintWebSocketHandler paintWebSocketHandler) {
		this.paintWebSocketHandler = paintWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(paintWebSocketHandler, "/ws").setAllowedOrigins("*");
	}
}
