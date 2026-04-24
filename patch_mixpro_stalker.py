#!/usr/bin/env python3
"""
COMPLETE Stalker fix based on Mixproplayer (com.freekdz.iptvpro) decompilation.

Key findings from Mixproplayer tb/o2.smali:
- SN = MD5(MAC).uppercase().substring(0, 13)
- deviceId = SHA-256(MAC).uppercase()
- deviceId2 = SHA-256(SN).uppercase()
- hw_version_2 = SHA-1(MAC)  *** NOT SHA-256 ***
- prehash = MD5(SN)
- signature = SHA-256(SN + MAC).uppercase()  (inferred from scanner)
- metrics = JSON with mac, sn, model, uid, random
- User-Agents: MAG200 for auth, MAG250 for requests, Chrome for fallback
- get_profile called with auth_second_step=1 in the same request
"""

import os
ROOT = os.path.expanduser("~/Forza113-Player")
os.chdir(ROOT)

p = 'data/src/main/java/com/kuqforza/data/remote/stalker/OkHttpStalkerApiService.kt'
with open(p, 'r') as f:
    c = f.read()

# ═══ 1. Fix buildStalkerDeviceProfile ═══
old_build = '''    val normalizedMac = macAddress.uppercase(Locale.ROOT)
    // Premium-compatible: SN = MD5(MAC), deviceId = SHA256(MAC), etc.
    val md5 = MessageDigest.getInstance("MD5")
        .digest(normalizedMac.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    val serialNumber = md5.take(13)
    val deviceId = stalkerDigest(normalizedMac)
    val deviceId2 = stalkerDigest(serialNumber)
    val signature = stalkerDigest(serialNumber + normalizedMac)'''

new_build = '''    val normalizedMac = macAddress.uppercase(Locale.ROOT)
    // Mixproplayer-compatible premium protocol
    val md5Full = MessageDigest.getInstance("MD5")
        .digest(normalizedMac.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    val serialNumber = md5Full.take(13)
    val deviceId = sha256Hex(normalizedMac)
    val deviceId2 = sha256Hex(serialNumber)
    val signature = sha256Hex(serialNumber + normalizedMac)'''

if old_build in c:
    c = c.replace(old_build, new_build, 1)
    print('1a. Fixed SN/deviceId generation')
else:
    # Try original StreamVault version
    old_build2 = '''    val normalizedMac = macAddress.uppercase(Locale.ROOT)
    val serialSeed = normalizedMac.replace(":", "")
    val serialNumber = serialSeed.takeLast(13).padStart(13, '0')
    val deviceId = stalkerDigest("device:$normalizedProfile:$normalizedMac")
    val deviceId2 = stalkerDigest("device2:$normalizedProfile:$normalizedMac")
    val signature = stalkerDigest("signature:$normalizedProfile:$normalizedMac:$normalizedTimezone")'''

    new_build2 = '''    val normalizedMac = macAddress.uppercase(Locale.ROOT)
    // Mixproplayer-compatible premium protocol
    val md5Full = MessageDigest.getInstance("MD5")
        .digest(normalizedMac.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    val serialNumber = md5Full.take(13)
    val deviceId = sha256Hex(normalizedMac)
    val deviceId2 = sha256Hex(serialNumber)
    val signature = sha256Hex(serialNumber + normalizedMac)'''

    if old_build2 in c:
        c = c.replace(old_build2, new_build2, 1)
        print('1b. Fixed SN/deviceId generation (original)')

# ═══ 2. Fix User-Agent: use MAG200 UA consistently ═══
old_ua_line = '        userAgent = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) $normalizedProfile stbapp ver: 2 rev: 250 Safari/533.3",'
new_ua_line = '        userAgent = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",'

if old_ua_line in c:
    c = c.replace(old_ua_line, new_ua_line, 1)
    print('2. Fixed User-Agent to MAG200')

# ═══ 3. Fix xUserAgent ═══
old_xua = '        xUserAgent = "Model: $normalizedProfile; Link: Ethernet"'
new_xua = '        xUserAgent = "Model: MAG250; Link: Ethernet,WiFi"'

if old_xua in c:
    c = c.replace(old_xua, new_xua, 1)
    print('3. Fixed X-User-Agent')

# ═══ 4. Fix buildProfileQuery - hw_version_2 must be SHA-1(MAC) ═══
old_hw = '''            "metrics" to """{"mac":"${profile.macAddress}","sn":"${profile.serialNumber}","model":"${profile.deviceProfile}","type":"STB","uid":"${profile.deviceId}","random":""}""",
            "hw_version_2" to stalkerDigest(profile.macAddress).lowercase(Locale.ROOT).take(40),'''

if old_hw in c:
    new_hw = '''            "metrics" to """{"mac":"${profile.macAddress}","sn":"${profile.serialNumber}","model":"MAG250","type":"STB","uid":"${profile.deviceId}","random":""}""",
            "hw_version_2" to sha1Hex(profile.macAddress),'''
    c = c.replace(old_hw, new_hw, 1)
    print('4a. Fixed metrics + hw_version_2 to SHA-1')
else:
    # Try original
    old_hw2 = '''            "metrics" to "{}",
            "hw_version_2" to profile.deviceProfile,'''
    if old_hw2 in c:
        new_hw2 = '''            "metrics" to """{"mac":"${profile.macAddress}","sn":"${profile.serialNumber}","model":"MAG250","type":"STB","uid":"${profile.deviceId}","random":""}""",
            "hw_version_2" to sha1Hex(profile.macAddress),'''
        c = c.replace(old_hw2, new_hw2, 1)
        print('4b. Fixed metrics + hw_version_2 (original)')

# ═══ 5. Fix prehash = MD5(SN) ═══
# Find and replace prehash
old_prehash = '"prehash" to "0"'
new_prehash = '"prehash" to md5Hex(profile.serialNumber)'
if old_prehash in c:
    c = c.replace(old_prehash, new_prehash, 1)
    print('5. Fixed prehash = MD5(SN)')

# Also try the other prehash format
old_prehash2 = '"prehash" to MessageDigest.getInstance("MD5").digest(profile.serialNumber.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it.toInt() and 0xFF) }.lowercase(Locale.ROOT)'
if old_prehash2 in c:
    c = c.replace(old_prehash2, '"prehash" to md5Hex(profile.serialNumber)', 1)
    print('5b. Fixed prehash (alt format)')

# ═══ 6. Add sha256Hex, sha1Hex, md5Hex helper functions ═══
old_digest = '''private fun stalkerDigest(seed: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }'''

new_digest = '''private fun stalkerDigest(seed: String): String = sha256Hex(seed)

private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it.toInt() and 0xFF) }

private fun sha1Hex(input: String): String =
    MessageDigest.getInstance("SHA-1")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

private fun md5Hex(input: String): String =
    MessageDigest.getInstance("MD5")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }'''

if old_digest in c:
    c = c.replace(old_digest, new_digest, 1)
    print('6. Added sha256Hex/sha1Hex/md5Hex helpers')

# Also check if md5Digest was already added
if 'private fun md5Digest' in c and 'private fun md5Hex' in c:
    # Remove duplicate
    c = c.replace('\nprivate fun md5Digest(seed: String): String =\n    MessageDigest.getInstance("MD5")\n        .digest(seed.toByteArray(Charsets.UTF_8))\n        .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }', '')
    print('6b. Removed duplicate md5Digest')

# ═══ 7. Fix Dalvik UA for handshake (before token) ═══
old_request_ua = '            .header("User-Agent", if (token.isNullOrBlank()) "Dalvik/2.1.0 (Linux; U; Android 14)" else profile.userAgent)'
if old_request_ua in c:
    # Keep it, this is correct - Dalvik before handshake, MAG after
    print('7. Dalvik UA for handshake already correct')
else:
    old_request_ua2 = '            .header("User-Agent", profile.userAgent)'
    if old_request_ua2 in c:
        new_request_ua2 = '            .header("User-Agent", if (token.isNullOrBlank()) "Dalvik/2.1.0 (Linux; U; Android 14; sdk_gphone64_x86_64 Build/UPB4.230623.005)" else profile.userAgent)'
        c = c.replace(old_request_ua2, new_request_ua2, 1)
        print('7. Added Dalvik UA for handshake')

# ═══ 8. Add Authorization: MAC for handshake ═══
old_auth = '''                token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }'''
new_auth = '''                if (token.isNullOrBlank()) {
                    header("Authorization", "MAC ${profile.macAddress}")
                } else {
                    header("Authorization", "Bearer $token")
                }'''
if old_auth in c:
    c = c.replace(old_auth, new_auth, 1)
    print('8. Added Authorization: MAC for handshake')

with open(p, 'w') as f:
    f.write(c)

print('\nDone! All Mixproplayer-compatible fixes applied.')
print('Push with: git add -A && git commit -m "mixproplayer stalker protocol" && git push')
