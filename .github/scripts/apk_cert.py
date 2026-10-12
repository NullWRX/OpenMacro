"""Print the SHA-256 of the signing certificate in an APK's v2 or v3 signature block."""
import hashlib
import struct
import sys


def signer_cert_sha256(path: str) -> str:
    data = open(path, "rb").read()
    eocd = data.rfind(b"PK\x05\x06")
    cd_offset = struct.unpack("<I", data[eocd + 16:eocd + 20])[0]
    if data[cd_offset - 16:cd_offset] != b"APK Sig Block 42":
        return "unsigned"
    size = struct.unpack("<Q", data[cd_offset - 24:cd_offset - 16])[0]
    block = data[cd_offset - size - 8:cd_offset - 24]
    pos = 8
    found = {}
    while pos < len(block):
        length = struct.unpack("<Q", block[pos:pos + 8])[0]
        pair_id = struct.unpack("<I", block[pos + 8:pos + 12])[0]
        value = block[pos + 12:pos + 8 + length]
        pos += 8 + length
        if pair_id not in (0x7109871A, 0xF05368C0):  # v2, v3
            continue
        signer_len = struct.unpack("<I", value[4:8])[0]
        signer = value[8:8 + signer_len]
        signed_len = struct.unpack("<I", signer[0:4])[0]
        signed = signer[4:4 + signed_len]
        digests_len = struct.unpack("<I", signed[0:4])[0]
        certs = signed[8 + digests_len:]
        cert_len = struct.unpack("<I", certs[0:4])[0]
        found[pair_id] = hashlib.sha256(certs[4:4 + cert_len]).hexdigest()
    return found.get(0xF05368C0) or found.get(0x7109871A) or "unsigned"


if __name__ == "__main__":
    print(signer_cert_sha256(sys.argv[1]))
