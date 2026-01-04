package com.colombus.common.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record TombstoneDTO(
	UUID opId,
	int voxelId,
	int vSeq,
	UUID actor,
	Instant timestamp
) {
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private UUID opId;
		private int voxelId;
		private int vSeq;
		private UUID actor;
		private Instant timestamp;

		public Builder opId(UUID opId) { this.opId = opId; return this; }
		public Builder voxelId(int voxelId) { this.voxelId = voxelId; return this; }
		public Builder vSeq(int vSeq) { this.vSeq = vSeq; return this; }
		public Builder actor(UUID actor) { this.actor = actor; return this; }
		public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

		public TombstoneDTO build() {
			return new TombstoneDTO(opId, voxelId, vSeq, actor, timestamp);
		}
	}
}
