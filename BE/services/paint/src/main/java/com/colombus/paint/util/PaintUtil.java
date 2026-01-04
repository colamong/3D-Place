package com.colombus.paint.util;

import org.springframework.stereotype.Component;

import com.colombus.common.domain.dto.ChunkIndexDTO;

@Component
public class PaintUtil {

	private static final int CHUNK_SIZE = 256;

	/**
	 * 로컬 인덱스(vix, viy, viz)를 24비트 정수(voxelId)로 압축
	 */
	public static int packXYZ(int x, int y, int z) {
		return ((x & 0xFF) << 16) | ((y & 0xFF) << 8) | (z & 0xFF);
	}

	public static String createChunkId(String worldName, int tx, int ty, int cix, int ciy, int ciz) {
		return String.format("{world:%s}:l0:tx%d:ty%d:x%d:y%d:z%d",
			worldName, tx, ty, cix, ciy, ciz);
	}

	public static String createChunkId(ChunkIndexDTO chunkIndex) {
		return createChunkId(
			chunkIndex.worldName(),
			chunkIndex.tx(),
			chunkIndex.ty(),
			chunkIndex.cix(),
			chunkIndex.ciy(),
			chunkIndex.ciz()
		);
	}
}