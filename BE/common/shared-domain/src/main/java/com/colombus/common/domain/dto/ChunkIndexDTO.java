package com.colombus.common.domain.dto;

public record ChunkIndexDTO(
	String worldName,
	int tx, // 타일 x
	int ty, // 타일 y
	int cix,
	int ciy,
	int ciz
) {
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String worldName;
		private int tx;
		private int ty;
		private int cix;
		private int ciy;
		private int ciz;

		public Builder worldName(String worldName) {this.worldName = worldName; return this;}
		public Builder tx(int tx) { this.tx = tx; return this; }
		public Builder ty(int ty) { this.ty = ty; return this; }
		public Builder cix(int cix) { this.cix = cix; return this; }
		public Builder ciy(int ciy) { this.ciy = ciy; return this; }
		public Builder ciz(int ciz) { this.ciz = ciz; return this; }

		public ChunkIndexDTO build() {
			return new ChunkIndexDTO(worldName, tx, ty, cix, ciy, ciz);
		}
	}
}