package com.colombus.ws.handler;

import com.colombus.ws.dto.PaintBroadcast;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener로부터 받은 PaintResponseDTO를 브로드캐스트
 * */
@Component
public class PaintWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(PaintWebSocketHandler.class);

	private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
	private final ObjectMapper objectMapper;

	public PaintWebSocketHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		sessions.add(session); // 구독
		log.info("WebSocket 새 클라이언트 연결: {}, 총 연결: {}", session.getId(), sessions.size());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		sessions.remove(session);
		log.info("WebSocket 클라이언트 연결 끊김: {}, 상태: {}, 총 연결: {}",
			session.getId(), status, sessions.size());
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		log.error("WebSocket 전송 에러 발생, 세션 ID: {}", session.getId(), exception);
		sessions.remove(session);
	}

	public void broadcast(PaintBroadcast response) {

		if (sessions.isEmpty()) {
			log.debug("연결된 클라이언트가 없어 브로드캐스트 스킵");
			return;
		}

		try {
			String messagePayload = objectMapper.writeValueAsString(response); // DTO 직렬화
			TextMessage message = new TextMessage(messagePayload);

			int successCount = 0;
			int failCount = 0;

			for (WebSocketSession session : sessions) {
				if (session.isOpen()) {
					try {
						session.sendMessage(message);
						successCount++;
					} catch (IOException e) {
						log.error("메시지 전송 실패, 세션 ID: {}", session.getId(), e);
						sessions.remove(session);
						failCount++;
					}
				}
			}

			log.info("브로드캐스트 완료: globalSeq={}, events={}, 성공={}, 실패={}",
				response.globalSeq(), response.events().size(), successCount, failCount);

		} catch (Exception e) {
			log.error("브로드캐스트 중 오류 발생", e);
		}
	}
}