package com.colombus.media.upload.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "media.s3")
public class UploadProps {

    /** 스테이징 업로드 버킷 */
    private String bucket;
    /** 스테이징 키 prefix (기본: "staging") */
    private String stagingPrefix = "staging";
    /** 최종 키 prefix (기본: "assets") */
    private String finalPrefix = "assets";
    /** Presign 유효 시간(분) */
    private int ttlMinutes = 5;
    private Boolean requireChecksum = true;

    public String bucket() { return bucket; }
    public void setBucket(String s) { this.bucket = s; }
    public String stagingPrefix() { return stagingPrefix; }
    public void setStagingPrefix(String s) { this.stagingPrefix = s; }
    public String finalPrefix() { return finalPrefix; }
    public void setfinalPrefix(String s) { this.finalPrefix = s; }
    public int ttlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int v) { this.ttlMinutes = v; }
    public Boolean requireChecksum() { return requireChecksum; }
    public void setRequireChecksum(Boolean b) { this.requireChecksum = b; }
}