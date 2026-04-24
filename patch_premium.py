import os
ROOT = os.path.expanduser("~/Forza113-Player")
os.chdir(ROOT)

fixes = 0

# ═══ 1. Fix OkHttpStalkerApiService.kt ═══
p1 = 'data/src/main/java/com/kuqforza/data/remote/stalker/OkHttpStalkerApiService.kt'
with open(p1, 'r') as f:
    c = f.read()

# 1a. Fix buildStalkerDeviceProfile
old = '''    val normalizedMac = macAddress.uppercase(Locale.ROOT)
    val serialSeed = normalizedMac.replace(":", "")
    val serialNumber = serialSeed.takeLast(13).padStart(13, '0')
    val deviceId = stalkerDigest("device:$normalizedProfile:$normalizedMac")
    val deviceId2 = stalkerDigest("device2:$normalizedProfile:$normalizedMac")
    val signature = stalkerDigest("signature:$normalizedProfile:$normalizedMac:$normalizedTimezone")'''

new = '''    val normalizedMac = macAddress.uppercase(Locale.ROOT)
    // QualiMac V2 premium protocol
    val snFull = MessageDigest.getInstance("MD5").digest(normalizedMac.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    val serialNumber = snFull.take(13)
    val deviceId = MessageDigest.getInstance("SHA-256").digest(normalizedMac.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    val deviceId2 = MessageDigest.getInstance("SHA-256").digest(serialNumber.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    val signature = MessageDigest.getInstance("SHA-256").digest((serialNumber + normalizedMac).toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it.toInt() and 0xFF) }'''

if old in c:
    c = c.replace(old, new, 1)
    fixes += 1
    print('1a. Fixed SN/deviceId/signature')

# 1b. Fix UA to Dalvik
old = '        userAgent = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) $normalizedProfile stbapp ver: 2 rev: 250 Safari/533.3",'
new = '        userAgent = "Dalvik/2.1.0 (Linux; U; Android 14; sdk_gphone64_x86_64",'
if old in c:
    c = c.replace(old, new, 1)
    fixes += 1
    print('1b. Fixed UA')

# 1c. Fix X-User-Agent
old = '        xUserAgent = "Model: $normalizedProfile; Link: Ethernet"'
new = '        xUserAgent = "Model: MAG250; Link: Ethernet,WiFi"'
if old in c:
    c = c.replace(old, new, 1)
    fixes += 1
    print('1c. Fixed X-User-Agent')

# 1d. Fix Cookie - add debug=1 and adid
old = '            .header("Cookie", "mac=${profile.macAddress}; stb_lang=${profile.locale}; timezone=${profile.timezone}")'
new = '            .header("Cookie", "mac=${profile.macAddress}; stb_lang=${profile.locale}; timezone=${profile.timezone}; debug=1; adid==" + MessageDigest.getInstance("SHA-1").digest(profile.macAddress.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xFF) })'
if old in c:
    c = c.replace(old, new, 1)
    fixes += 1
    print('1d. Fixed Cookie with adid')

# 1e. Fix buildProfileQuery - hw_version_2 hardcoded + metrics
old = '''            "metrics" to "{}",
            "hw_version_2" to profile.deviceProfile,'''
new = '''            "metrics" to java.net.URLEncoder.encode("{\\"mac\\":\\"${profile.macAddress}\\",\\"sn\\":\\"${profile.serialNumber}\\",\\"model\\":\\"MAG250\\",\\"type\\":\\"STB\\",\\"uid\\":\\"${profile.deviceId}\\",\\"random\\":\\"\\"}", "UTF-8"),
            "hw_version_2" to "adece2bb1c34aa23affc14565c630e11f6e73ad1",'''
if old in c:
    c = c.replace(old, new, 1)
    fixes += 1
    print('1e. Fixed metrics + hw_version_2')

# 1f. Fix prehash
old = '            "prehash" to "0"'
new = '            "prehash" to MessageDigest.getInstance("MD5").digest(profile.serialNumber.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xFF) }'
if old in c:
    c = c.replace(old, new, 1)
    fixes += 1
    print('1f. Fixed prehash')

# 1g. Fix authenticate - 3 get_profile calls
old_auth = '''            val session = StalkerSession(loadUrl = loadUrl, portalReferer = referer, token = token)
            val profileResult = runCatching {
                requestJson(
                    url = loadUrl,
                    profile = profile,
                    referer = referer,
                    token = token,
                    query = buildProfileQuery(profile)
                )
            }
            val profilePayload = profileResult.getOrElse { error ->
                lastError = error
                continue
            }

            return Result.success(session to profilePayload.toProviderProfile())'''

new_auth = '''            val session = StalkerSession(loadUrl = loadUrl, portalReferer = referer, token = token)

            // get_profile #1: SN only, device_id/device_id2/signature EMPTY
            val profileQuery1 = buildProfileQuery(profile).toMutableMap()
            profileQuery1["device_id"] = ""
            profileQuery1["device_id2"] = ""
            profileQuery1["signature"] = ""
            runCatching { requestJson(url = loadUrl, profile = profile, referer = referer, token = token, query = profileQuery1) }

            // get_profile #2: with device_id = device_id2 = DEVICE1, signature = SIGN
            val profileQuery2 = buildProfileQuery(profile).toMutableMap()
            profileQuery2["device_id"] = profile.deviceId
            profileQuery2["device_id2"] = profile.deviceId  // Same as device_id, NOT device_id2!
            profileQuery2["signature"] = profile.signature
            runCatching { requestJson(url = loadUrl, profile = profile, referer = referer, token = token, query = profileQuery2) }

            // get_profile #3: minimal auth_second_step
            runCatching {
                requestJson(
                    url = loadUrl, profile = profile, referer = referer, token = token,
                    query = mapOf(
                        "type" to "stb",
                        "action" to "get_profile",
                        "auth_second_step" to "1",
                        "hw_version_2" to "adece2bb1c34aa23affc14565c630e11f6e73ad1",
                        "JsHttpRequest" to "1-xml"
                    )
                )
            }

            // get_main_info for account details
            val infoResult = runCatching {
                requestJson(
                    url = loadUrl, profile = profile, referer = referer, token = token,
                    query = mapOf(
                        "type" to "account_info",
                        "action" to "get_main_info",
                        "JsHttpRequest" to "1-xml"
                    )
                )
            }
            val infoPayload = infoResult.getOrElse { error ->
                lastError = error
                continue
            }

            return Result.success(session to infoPayload.toProviderProfile())'''

if old_auth in c:
    c = c.replace(old_auth, new_auth, 1)
    fixes += 1
    print('1g. Fixed auth: 3 get_profile calls')

# 1h. Remove Authorization from handshake
old_noauth = '''            .apply {
                token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }'''
new_noauth = '''            .apply {
                token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                // No Authorization header for handshake (QualiMac V2)
            }'''
if old_noauth in c:
    c = c.replace(old_noauth, new_noauth, 1)
    print('1h. Auth header already correct (Bearer only when token present)')

with open(p1, 'w') as f:
    f.write(c)

print(f'\nTotal: {fixes} fixes')
print('Done!')
