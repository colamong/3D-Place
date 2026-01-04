package com.colombus.clan.model.type;

public enum ClanMemberRole {
    MASTER(3),
    OFFICER(2),
    MEMBER(1);

    private final int power;

    ClanMemberRole(int power) {
        this.power = power;
    }

    public int power() {
        return power;
    }

    /** this가 other보다 권한이 높은지 */
    public boolean isHigherThan(ClanMemberRole other) {
        return this.power > other.power;
    }

    /** this가 other를 관리할 수 있는지 (strict 하게 상위여야 함) */
    public boolean canManage(ClanMemberRole target) {
        return this.power > target.power;
    }
}