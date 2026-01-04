package com.colombus.media.upload.repository;

import static com.colombus.media.jooq.tables.StagingUploadTicket.STAGING_UPLOAD_TICKET;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import com.colombus.common.kafka.subject.model.type.AssetPurpose;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.colombus.common.utility.time.TimeConv;
import com.colombus.media.exception.InternalErrorException;
import com.colombus.media.exception.SubejectNotFoundException;
import com.colombus.media.upload.model.StagingTicket;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StagingTicketRepository {

    private final DSLContext dsl;

    public boolean insertPending(
        UUID assetId,
        SubjectKind kind,
        UUID subjectId,
        AssetPurpose purpose,
        String s3Key,
        Instant expiresAt
    ) {

        try {
            int rows = dsl.insertInto(STAGING_UPLOAD_TICKET)
                        .set(STAGING_UPLOAD_TICKET.ASSET_ID, assetId)
                        .set(STAGING_UPLOAD_TICKET.SUBJECT_KIND, kind)
                        .set(STAGING_UPLOAD_TICKET.SUBJECT_ID, subjectId)
                        .set(STAGING_UPLOAD_TICKET.PURPOSE, purpose)
                        .set(STAGING_UPLOAD_TICKET.S3_KEY, s3Key)
                        .set(STAGING_UPLOAD_TICKET.EXPIRES_AT, TimeConv.toUtcOdt(expiresAt))
                        .set(STAGING_UPLOAD_TICKET.STATUS, "PENDING")
                        .execute();
            
            if (rows > 1) {
                throw new InternalErrorException("insertPending affectedRows=" + rows);
            }

            return true;
        } catch (DataIntegrityViolationException e) {
            if (isSubjectNotAlive(e)) {
                throw new SubejectNotFoundException(e);
            }
            throw e;
        }
    }

    /** PENDING 상태 티켓 조회 */
    public Optional<StagingTicket> findPending(UUID assetId) {
        return dsl.selectFrom(STAGING_UPLOAD_TICKET)
                .where(STAGING_UPLOAD_TICKET.ASSET_ID.eq(assetId)
                    .and(STAGING_UPLOAD_TICKET.STATUS.eq("PENDING")))
                .fetchOptional(r -> new StagingTicket(
                        r.getAssetId(),
                        r.getSubjectKind(),
                        r.getSubjectId(),
                        r.getPurpose(),
                        r.getS3Key(),
                        TimeConv.toInstant(r.getExpiresAt()),
                        r.getStatus(),
                        TimeConv.toInstant(r.getCreatedAt()),
                        TimeConv.toInstant(r.getUsedAt())
                ));
    }

    /** 만료 처리: 만료 시각 지난 PENDING → EXPIRED */
    public int expireDue(Instant now) {
        return dsl.update(STAGING_UPLOAD_TICKET)
                .set(STAGING_UPLOAD_TICKET.STATUS, "EXPIRED")
                .set(STAGING_UPLOAD_TICKET.UPDATED_AT, TimeConv.nowUtcOdt())
                .where(STAGING_UPLOAD_TICKET.STATUS.eq("PENDING")
                    .and(STAGING_UPLOAD_TICKET.EXPIRES_AT.lt(TimeConv.toUtcOdt(now))))
                .execute();
    }

    /** 사용 처리: PENDING → USED (1회성 보장) */
    public boolean markUsed(UUID assetId, Instant usedAt) {
        return dsl.update(STAGING_UPLOAD_TICKET)
                .set(STAGING_UPLOAD_TICKET.STATUS, "USED")
                .set(STAGING_UPLOAD_TICKET.USED_AT, TimeConv.toUtcOdt(usedAt))
                .set(STAGING_UPLOAD_TICKET.UPDATED_AT, TimeConv.nowUtcOdt())
                .where(STAGING_UPLOAD_TICKET.ASSET_ID.eq(assetId)
                    .and(STAGING_UPLOAD_TICKET.STATUS.eq("PENDING")))
                .execute() == 1;
    }

    public int markExpired(Instant now) {
        return dsl.update(STAGING_UPLOAD_TICKET)
            .set(STAGING_UPLOAD_TICKET.STATUS, "EXPIRED")
            .set(STAGING_UPLOAD_TICKET.UPDATED_AT, TimeConv.nowUtcOdt())
            .where(STAGING_UPLOAD_TICKET.STATUS.eq("PENDING"))
                .and(STAGING_UPLOAD_TICKET.EXPIRES_AT.le(TimeConv.toUtcOdt(now)))
            .execute();
    }

    private boolean isSubjectNotAlive(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();

        String msg = cause.getMessage();
        if (msg == null) {
            return false;
        }

        return msg.contains("Subject") && msg.contains("not alive");
    }
}