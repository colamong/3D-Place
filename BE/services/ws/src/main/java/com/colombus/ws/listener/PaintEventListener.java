package com.colombus.ws.listener;

import java.util.List;

import com.colombus.common.domain.dto.PaintResponse;
import com.colombus.ws.dto.PaintBroadcast;
import com.colombus.ws.handler.PaintWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka에서 PaintResponseDTO를 받아서 Handler에 전달
 */
@Component
public class PaintEventListener {

	private static final Logger log = LoggerFactory.getLogger(PaintEventListener.class);
	private final PaintWebSocketHandler paintWebSocketHandler;

	public PaintEventListener(PaintWebSocketHandler paintWebSocketHandler) {
		this.paintWebSocketHandler = paintWebSocketHandler;
	}

	@KafkaListener(
		topics = "colombus-paint-events",
		groupId = "colombus-ws",
		batch = "true"
	)
	public void listenPaintEvents(
		List<PaintResponse> responses,
		@Header(KafkaHeaders.OFFSET) List<Long> offsets
	) {
		if (responses.isEmpty()) return;

		// 리스트의 마지막 offset을 globalSeq로 사용
		long globalSeq = offsets.getLast();

		log.info("Kafka 배치 메시지 수신: globalSeq={}, 총 {}개 이벤트", globalSeq, responses.size());

		// 리스트를 PaintBroadcastDTO로 감싸서 전송
		PaintBroadcast broadcastBatch = PaintBroadcast.builder()
			.globalSeq(globalSeq)
			.events(responses)
			.build();

		paintWebSocketHandler.broadcast(broadcastBatch);
	}
}