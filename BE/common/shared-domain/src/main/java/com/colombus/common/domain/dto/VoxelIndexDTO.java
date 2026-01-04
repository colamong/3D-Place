package com.colombus.common.domain.dto;

public record VoxelIndexDTO(
	int vix,
	int viy,
	int viz) {
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private int vix;
		private int viy;
		private int viz;

		public Builder vix(int vix) { this.vix = vix; return this; }
		public Builder viy(int viy) { this.viy = viy; return this; }
		public Builder viz(int viz) { this.viz = viz; return this; }

		public VoxelIndexDTO build() {
			return new VoxelIndexDTO(vix, viy, viz);
		}
	}
}