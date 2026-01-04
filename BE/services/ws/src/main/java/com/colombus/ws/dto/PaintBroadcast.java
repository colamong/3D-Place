package com.colombus.ws.dto;

import java.util.Collections;
import java.util.List;

import com.colombus.common.domain.dto.PaintResponse;

/**
 * WebSocket으로 브로드캐스트 될 최종 DTO(Wrapper)
 * 1개 이상의 PaintResponseDTO를 event 리스트에 담아서 전송
 */
public record PaintBroadcast(
	long globalSeq,
	List<PaintResponse> events
) {
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private long globalSeq;
		private List<PaintResponse> events;

		public Builder globalSeq(long globalSeq) { this.globalSeq = globalSeq; return this; }
		public Builder events(List<PaintResponse> events) { this.events = events; return this; }
		// 1건
		public Builder event(PaintResponse event) { this.events = Collections.singletonList(event); return this; }

		public PaintBroadcast build() {
			return new PaintBroadcast(globalSeq, events);
		}
	}

	public static PaintBroadcast fromSingle(long globalSeq, PaintResponse event) {
		return new PaintBroadcast(globalSeq, Collections.singletonList(event));
	}
}
