package com.colombus.clan.messaging.kafka.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topics")
public class TopicProperties {

    /** subject projection */
    private String subjectRegistryV1 = "subject.registry.v1";
    private String subjectAssetUpsertV1 = "subject.asset-upsert.v1";

    /** membership projection */
    private String clanMembershipCurrentV1 = "clan.membership.current.v1";
    
    /** DLQs */
    private String registrationDlq = "registration.dlq";
    private String linkDlq = "link.dlq";
    private String unlinkDlq = "unlink.dlq";

    /** 공통 파티션/복제 (필요 시 override) */
    private int defaultPartitions = 3;
    private short defaultReplicationFactor = 1;

    public String getSubjectRegistryV1() { return subjectRegistryV1; }
    public void setSubjectRegistryV1(String v) { this.subjectRegistryV1 = v; }
    public String getSubjectAssetUpsertV1() { return subjectAssetUpsertV1; }
    public void setSubjectAssetUpsertV1(String v) { this.subjectAssetUpsertV1 = v; }

    public String getClanMembershipCurrentV1() { return clanMembershipCurrentV1; }
    public void setClanMembershipCurrentV1(String v) { this.clanMembershipCurrentV1 = v; }

    public String getRegistrationDlq() { return registrationDlq; }
    public void setRegistrationDlq(String v) { this.registrationDlq = v; }
    public String getLinkDlq() { return linkDlq; }
    public void setLinkDlq(String v) { this.linkDlq = v; }
    public String getUnlinkDlq() { return unlinkDlq; }
    public void setUnlinkDlq(String v) { this.unlinkDlq = v; }
    
    public int getDefaultPartitions() { return defaultPartitions; }
    public void setDefaultPartitions(int v) { this.defaultPartitions = v; }
    public short getDefaultReplicationFactor() { return defaultReplicationFactor; }
    public void setDefaultReplicationFactor(short v) { this.defaultReplicationFactor = v; }
}