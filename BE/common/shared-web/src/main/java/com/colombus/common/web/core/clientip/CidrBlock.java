package com.colombus.common.web.core.clientip;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

public final class CidrBlock {
    private final InetAddress network;
    private final int prefixLength;
    private final byte[] mask;
    private final byte[] networkBytes;

    private CidrBlock(InetAddress network, int prefixLength, byte[] mask, byte[] networkBytes) {
        this.network = network;
        this.prefixLength = prefixLength;
        this.mask = mask;
        this.networkBytes = networkBytes;
    }

    /** "192.168.0.0/16", "::1/128" 형태 파싱 */
    public static CidrBlock parse(String cidr) {
        Objects.requireNonNull(cidr, "cidr");
        try {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            InetAddress net = InetAddress.getByName(parts[0].trim());
            int prefix = Integer.parseInt(parts[1].trim());

            byte[] addr = net.getAddress();
            int bits = addr.length * 8;
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Invalid prefix: " + prefix);

            byte[] mask = prefixMask(addr.length, prefix);
            byte[] netBytes = new byte[addr.length];
            for (int i = 0; i < addr.length; i++) netBytes[i] = (byte) (addr[i] & mask[i]);

            InetAddress normalized = InetAddress.getByAddress(netBytes);
            return new CidrBlock(normalized, prefix, mask, netBytes);
        } catch (UnknownHostException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
        }
    }

    /** 기존 코드 호환용: 포함 여부 */
    public boolean matches(InetAddress addr) {
        return contains(addr);
    }

    /** 포함 여부 (새 이름) */
    public boolean contains(InetAddress addr) {
        byte[] a = addr.getAddress();
        if (a.length != mask.length) return false; // IPv4 vs IPv6 길이 다르면 불일치
        for (int i = 0; i < a.length; i++) {
            if ((a[i] & mask[i]) != (networkBytes[i] & mask[i])) return false;
        }
        return true;
    }

    public InetAddress network() { return network; }
    public int prefixLength() { return prefixLength; }

    @Override public String toString() { return network.getHostAddress() + "/" + prefixLength; }

    private static byte[] prefixMask(int len, int prefix) {
        byte[] m = new byte[len];
        int full = prefix / 8;
        int rest = prefix % 8;
        Arrays.fill(m, 0, full, (byte) 0xFF);
        if (full < len && rest > 0) m[full] = (byte) (0xFF << (8 - rest));
        return m;
    }
}