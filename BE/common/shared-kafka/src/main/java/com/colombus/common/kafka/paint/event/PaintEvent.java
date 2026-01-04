package com.colombus.common.kafka.paint.event;

import java.time.Instant;
import java.util.UUID;

import com.colombus.common.domain.dto.ChunkIndexDTO;
import com.colombus.common.domain.dto.VoxelIndexDTO;
import com.colombus.common.kafka.paint.model.PaintColorSchema;
import com.colombus.common.kafka.paint.model.PaintOperationType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PaintEvent(
	UUID opId,
	VoxelIndexDTO voxelIndex,
	ChunkIndexDTO chunkIndex,
	Integer faceMask,
	Integer vSeq,
	PaintColorSchema colorSchema,
	String colorBytes,
	UUID actor,
	@JsonProperty("ts") Instant timestamp,
	PaintOperationType operationType
) {}