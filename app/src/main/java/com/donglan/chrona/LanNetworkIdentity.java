package com.donglan.chrona;

/** Equal private IPv4 addresses can belong to different LANs. */
final class LanNetworkIdentity {
    final long handle;
    final String address;
    LanNetworkIdentity(long handle, String address) { this.handle = handle; this.address = address; }
    boolean matches(LanNetworkIdentity current) {
        return current != null && handle == current.handle && address.equals(current.address);
    }
    boolean lost(long networkHandle) { return handle == networkHandle; }
}
