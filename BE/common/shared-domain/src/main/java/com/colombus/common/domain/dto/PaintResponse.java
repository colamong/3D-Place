package com.colombus.common.domain.dto;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record PaintResponse(
	UUID opId,
	VoxelIndexDTO voxelIndex,
	ChunkIndexDTO chunkIndex,
	Integer faceMask,
	Integer vSeq,
	DeltaDTO.ColorSchema colorSchema,
	String colorBytes,
	UUID actor,
	Instant timestamp,
	OperationType operationType
) {
	public enum OperationType {
		UPSERT, ERASE
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private UUID opId;
		private VoxelIndexDTO voxelIndex;
		private ChunkIndexDTO chunkIndex;
		private Integer faceMask;
		private Integer vSeq;
		private DeltaDTO.ColorSchema colorSchema;
		private String colorBytes;
		private UUID actor;
		private Instant timestamp;
		private OperationType operationType;

		public Builder opId(UUID opId) { this.opId = opId; return this; }
		public Builder voxelIndex(VoxelIndexDTO voxelIndex) { this.voxelIndex = voxelIndex; return this; }
		public Builder chunkIndex(ChunkIndexDTO chunkIndex) { this.chunkIndex = chunkIndex; return this; }
		public Builder faceMask(Integer faceMask) { this.faceMask = faceMask; return this; }
		public Builder vSeq(Integer vSeq) { this.vSeq = vSeq; return this; }
		public Builder colorSchema(DeltaDTO.ColorSchema colorSchema) { this.colorSchema = colorSchema; return this; }
		public Builder colorBytes(byte[] colorBytes) {
			if (colorBytes != null) {
				this.colorBytes = Base64.getEncoder().encodeToString(colorBytes);
			}
			return this;
		}

		public Builder colorBytesString(String colorBytes) { this.colorBytes = colorBytes; return this; }
		public Builder actor(UUID actor) { this.actor = actor; return this; }
		public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
		public Builder operationType(OperationType operationType) { this.operationType = operationType; return this; }

		public PaintResponse build() {
			return new PaintResponse(
				opId, voxelIndex, chunkIndex, faceMask, vSeq, colorSchema,
				colorBytes, actor, timestamp, operationType
			);
		}
	}

	/**
	 * DeltaDTO로부터 PaintResponseDTO 생성 (UPSERT)
	 */
	public static PaintResponse fromDelta(DeltaDTO delta, VoxelIndexDTO voxelIndex, ChunkIndexDTO chunkIndex) {
		return PaintResponse.builder()
			.opId(delta.opId())
			.voxelIndex(voxelIndex)
			.chunkIndex(chunkIndex)
			.faceMask(delta.faceMask())
			.vSeq(delta.vSeq())
			.colorSchema(delta.colorSchema())
			.colorBytes(delta.colorBytes())
			.actor(delta.actor())
			.timestamp(delta.timestamp())
			.operationType(OperationType.UPSERT)
			.build();
	}

	/**
	 * TombstoneDTO로부터 PaintResponseDTO 생성 (ERASE)
	 */
	public static PaintResponse fromTombstone(TombstoneDTO tombstone, VoxelIndexDTO voxelIndex, ChunkIndexDTO chunkIndex, int vSeq) {
		return PaintResponse.builder()
			.opId(tombstone.opId())
			.voxelIndex(voxelIndex)
			.chunkIndex(chunkIndex)
			.vSeq(vSeq)
			.actor(tombstone.actor())
			.timestamp(tombstone.timestamp())
			.operationType(OperationType.ERASE)
			.build();
	}

	public static PaintResponse fromDelta(DeltaDTO delta, ChunkInfo c) {
		VoxelIndexDTO voxelIndex = parseVoxelIndex(delta.voxelId());
		ChunkIndexDTO chunkIndex = new ChunkIndexDTO(c.worldName(), c.tx(), c.ty(), c.x(), c.y(), c.z());

		return PaintResponse.builder()
				.opId(delta.opId())
				.voxelIndex(voxelIndex)
				.chunkIndex(chunkIndex)
				.faceMask(delta.faceMask())
				.vSeq(delta.vSeq())
				.colorSchema(delta.colorSchema())
				.colorBytes(delta.colorBytes())
				.actor(delta.actor())
				.timestamp(delta.timestamp())
				.operationType(OperationType.UPSERT)
				.build();
	}

	private static VoxelIndexDTO parseVoxelIndex(int voxelId) {
		int x = (voxelId >> 16) & 0xFF;
		int y = (voxelId >> 8) & 0xFF;
		int z = voxelId & 0xFF;
		return new VoxelIndexDTO(x, y, z);
	}
}