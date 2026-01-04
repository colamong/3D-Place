package com.colombus.paint.dto;

import java.util.UUID;

/**
 * Redis에 저장된 Voxel의 현재 상태 정보
 * vSeq 검증용
 */
public record VoxelState(
	UUID opId,
	int vSeq,
	boolean exists  // voxel이 존재하는지 여부
) {
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private UUID opId;
		private int vSeq;
		private boolean exists = true;

		public Builder opId(UUID opId) { this.opId = opId; return this; }
		public Builder vSeq(int vSeq) { this.vSeq = vSeq; return this; }
		public Builder exists(boolean exists) { this.exists = exists; return this; }

		public VoxelState build() {
			return new VoxelState(opId, vSeq, exists);
		}
	}

	/**
	 * 존재하지 않는 voxel 상태
	 */
	public static VoxelState notExists() {
		return new VoxelState(null, 0, false);
	}
}