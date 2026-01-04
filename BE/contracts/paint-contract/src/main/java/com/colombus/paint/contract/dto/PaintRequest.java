package com.colombus.paint.contract.dto;

import com.colombus.common.domain.dto.ChunkIndexDTO;
import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.common.domain.dto.VoxelIndexDTO;

import java.time.Instant;
import java.util.UUID;

public record PaintRequest(
	UUID opId,
	UUID existingOpId,
	int vSeq,
	VoxelIndexDTO voxelIndex,
	ChunkIndexDTO chunkIndex,
	int faceMask,
	DeltaDTO.ColorSchema colorSchema,
	byte[] colorBytes,
	Instant timestamp,
	String policyTags
) {
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private UUID opId;
		private UUID existingOpId;
		private int vSeq;
		private VoxelIndexDTO voxelIndex;
		private ChunkIndexDTO chunkIndex;
		private int faceMask;
		private DeltaDTO.ColorSchema colorSchema;
		private byte[] colorBytes;
		private Instant timestamp;
		private String policyTags;

		public Builder opId(UUID opId) { this.opId = opId; return this; }
		public Builder existingOpId(UUID existingOpId) { this.existingOpId = existingOpId; return this; }
		public Builder vSeq(int vSeq) { this.vSeq = vSeq; return this; }
		public Builder voxelIndex(VoxelIndexDTO voxelIndex) { this.voxelIndex = voxelIndex; return this; }
		public Builder chunkIndex(ChunkIndexDTO chunkIndex) { this.chunkIndex = chunkIndex; return this; }
		public Builder faceMask(int faceMask) { this.faceMask = faceMask; return this; }
		public Builder colorSchema(DeltaDTO.ColorSchema colorSchema) { this.colorSchema = colorSchema; return this; }
		public Builder colorBytes(byte[] colorBytes) { this.colorBytes = colorBytes; return this; }
		public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
		public Builder policyTags(String policyTags) { this.policyTags = policyTags; return this; }

		public PaintRequest build() {
			return new PaintRequest(opId, existingOpId, vSeq, voxelIndex, chunkIndex, faceMask,
				colorSchema, colorBytes, timestamp, policyTags);
		}
	}
}