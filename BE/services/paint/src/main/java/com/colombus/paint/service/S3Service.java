package com.colombus.paint.service;

import java.util.UUID;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.paint.dto.VoxelState;
import reactor.core.publisher.Mono;

/**
 * S3에서 voxel 데이터를 조회하는 서비스
 */
public interface S3Service {

	/**
	 * S3에서 특정 voxel의 상태를 조회
	 *
	 * @param chunkId 청크 ID
	 * @param voxelId voxel ID
	 * @return voxel 상태 (존재하지 않으면 VoxelStateDTO.notExists())
	 */
	Mono<VoxelState> getVoxelState(String chunkId, int voxelId);

	/**
	 * S3에서 특정 opId로 Delta 조회
	 *
	 * @param chunkId 청크 ID
	 * @param opId 행위에 대한 ID(각 voxel에 대한 요청 ID)
	 * @return DeltaDTO
	 * */
	Mono<DeltaDTO> getDeltaByOpId(String chunkId, UUID opId);
}