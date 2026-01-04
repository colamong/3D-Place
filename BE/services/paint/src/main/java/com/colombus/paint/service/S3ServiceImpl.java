package com.colombus.paint.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.paint.dto.VoxelState;
import com.colombus.paint.exception.PaintErrorCode;
import com.colombus.paint.exception.PaintInputException;
import com.colombus.paint.exception.S3OperationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class S3ServiceImpl implements S3Service {

	private static final Logger log = LoggerFactory.getLogger(S3ServiceImpl.class);

	private final S3Client s3Client;
	private final ObjectMapper objectMapper;

	@Value("${spring.cloud.aws.s3.bucket-name}")
	private String bucketName;

	private static final Pattern CHUNK_ID_PATTERN = Pattern.compile(
		"\\{world:([^}]+)\\}:l(\\d+):tx(-?\\d+):ty(-?\\d+):x(-?\\d+):y(-?\\d+):z(-?\\d+)"
	);

	public S3ServiceImpl(S3Client s3Client, ObjectMapper objectMapper) {
		this.s3Client = s3Client;
		this.objectMapper = objectMapper;
	}

	/**
	 * S3에서 vSeq를 찾는 작업(find...)을 webflux(비동기) 환경에서 안전하게 실행
	 * */
	@Override
	public Mono<VoxelState> getVoxelState(String chunkId, int voxelId) {
		// S3 SDK는 동기 방식, 별도 스레드에서 비동기적으로 실행 필요
		// S3에서 파일을 가져오는 무거운 작업을(find..) mono로 감싸서
		return Mono.fromCallable(() -> findVoxelStateInLatestSnapshot(chunkId, voxelId))
			.subscribeOn(Schedulers.boundedElastic()) // webflux의 메인 스레드가 아닌, 별도의 I/O 작업용 스레드 풀에서 실행(메인 멈춤 방지)
			.flatMap(optionalState -> optionalState.map(Mono::just).orElse(Mono.empty()))
			// S3 조회 실패 또는 VoxelId가 스냅샷에 없으면 'notExists' 반환
			.switchIfEmpty(Mono.just(VoxelState.notExists()));
	}

	/**
	 * S3에서 opId로 DeltaDTO를 찾는 작업(find...)을 webflux(비동기) 환경에서 안전하게 실행
	 */
	@Override
	public Mono<DeltaDTO> getDeltaByOpId(String chunkId, UUID opId) {
		// S3 SDK는 동기 방식, 별도 스레드에서 비동기적으로 실행 필요
		return Mono.fromCallable(() -> findDeltaByOpIdInLatestSnapshot(chunkId, opId))
			.subscribeOn(Schedulers.boundedElastic()) // 별도의 I/O 작업용 스레드 풀에서 실행
			// S3 조회 실패 또는 opId가 스냅샷에 없으면 Mono.empty() 반환
			.flatMap(optionalDelta -> optionalDelta.map(Mono::just).orElse(Mono.empty()));
	}

	// S3 스냅샷에서 VoxelState 찾기
	private Optional<VoxelState> findVoxelStateInLatestSnapshot(String chunkId, int voxelId) {

		// chunkId를 파싱해서 ChunkInfo 객체 생성
		ChunkInfo chunkInfo = parseChunkIdToInfo(chunkId)
			.orElseThrow(() -> new PaintInputException(PaintErrorCode.INVALID_CHUNKID_FORMAT));

		// S3 Key 빌드
		String s3Key = buildS3Key(chunkInfo);
		log.debug("S3 스냅샷 조회 시도: Key={}", s3Key);

		// S3에서 파일 가져오기
		Optional<String> snapshotJson = getFromS3(s3Key);

		if (snapshotJson.isEmpty()) {
			log.warn("S3에 스냅샷 없음: {}", s3Key);
			return Optional.empty();
		}

		// 전체 스냅샷 JSON 파싱 및 VoxelId 검색 - TODO: 변환 전에 탐색 고려
		try {
			List<DeltaDTO> deltas = objectMapper.readValue(snapshotJson.get(), new TypeReference<>() {});

			for (DeltaDTO delta : deltas) {
				if (delta.voxelId() == voxelId) {
					log.info("S3 스냅샷에서 Voxel 발견: voxelId={}, vSeq={}", voxelId, delta.vSeq());

					return Optional.of(VoxelState.builder()
						.opId(delta.opId())
						.vSeq(delta.vSeq())
						.exists(true)
						.build());
				}
			}

			log.warn("S3 스냅샷에 VoxelId 없음: {}", voxelId);
			return Optional.empty();

		} catch (Exception e) {
			log.error("S3 스냅샷 JSON 파싱 실패", e);
			throw new S3OperationException(PaintErrorCode.DELTA_PARSE_FAILED);
		}
	}

	// S3 스냅샷에서 opId로 DeltaDTO 찾기
	private Optional<DeltaDTO> findDeltaByOpIdInLatestSnapshot(String chunkId, UUID opId) {

		// chunkId를 파싱해서 ChunkInfo 객체 생성
		ChunkInfo chunkInfo = parseChunkIdToInfo(chunkId)
			.orElseThrow(() -> new PaintInputException(PaintErrorCode.INVALID_CHUNKID_FORMAT));

		// S3 Key 빌드
		String s3Key = buildS3Key(chunkInfo);
		log.debug("S3 스냅샷 조회 시도 (by opId): Key={}", s3Key);

		// S3에서 파일 가져오기
		Optional<String> snapshotJson = getFromS3(s3Key);

		if (snapshotJson.isEmpty()) {
			log.warn("S3에 스냅샷 없음: {}", s3Key);

			return Optional.empty();
		}

		// 전체 스냅샷 JSON 파싱 및 opId 검색
		try {
			List<DeltaDTO> deltas = objectMapper.readValue(snapshotJson.get(), new TypeReference<>() {});

			for (DeltaDTO delta : deltas) {
				if (delta.opId().equals(opId)) {
					log.info("S3 스냅샷에서 Delta 발견: opId={}", opId);

					// DeltaDTO 자체를 반환
					return Optional.of(delta);
				}
			}

			log.warn("S3 스냅샷에 opId 없음: {}", opId);
			return Optional.empty();

		} catch (Exception e) {
			log.error("S3 스냅샷 JSON 파싱 실패", e);
			throw new S3OperationException(PaintErrorCode.DELTA_PARSE_FAILED);
		}
	}

	private Optional<String> getFromS3(String s3Key) {
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
			.bucket(bucketName)
			.key(s3Key)
			.build();

		try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest)) {
			String content = new String(s3Object.readAllBytes(), StandardCharsets.UTF_8);
			if (content.isBlank() || content.equals("[]")) {
				return Optional.empty();
			}
			return Optional.of(content);
		} catch (NoSuchKeyException e) {
			log.warn("S3에 파일 없음: {}", s3Key);
			return Optional.empty(); // 404
		} catch (SdkException | IOException e) { // S3 연결 / IO 오류
			log.error("S3 파일({}) 조회 중 오류 발생", s3Key, e);
			throw new S3OperationException(PaintErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
		}
	}

	// 최신 snapshot 가져오는 key
	private String buildS3Key(ChunkInfo info) {
		return String.format("snapshot/%s/latest/l%d/tx%d/ty%d/x%d/y%d/z%d.json",
			info.worldName(), info.lod(),
			info.tx(), info.ty(),
			info.cix(), info.ciy(), info.ciz()
		);
	}

	private Optional<ChunkInfo> parseChunkIdToInfo(String chunkId) {
		Matcher matcher = CHUNK_ID_PATTERN.matcher(chunkId);
		if (matcher.matches()) {
			return Optional.of(new ChunkInfo(
				matcher.group(1), // worldName
				Integer.parseInt(matcher.group(2)), // lod
				Integer.parseInt(matcher.group(3)), // tx
				Integer.parseInt(matcher.group(4)), // ty
				Integer.parseInt(matcher.group(5)), // cix (x)
				Integer.parseInt(matcher.group(6)), // ciy (y)
				Integer.parseInt(matcher.group(7))  // ciz (z)
			));
		}
		log.error("chunkId 파싱 실패: {}", chunkId);
		return Optional.empty();
	}

	private record ChunkInfo(String worldName, int lod, int tx, int ty, int cix, int ciy, int ciz) {}
}