import os
ROOT = os.path.expanduser("~/Forza113-Player")
os.chdir(ROOT)

# ═══ 1. StalkerApiService.kt - add field to StalkerDeviceProfile ═══
p1 = 'data/src/main/java/com/kuqforza/data/remote/stalker/StalkerApiService.kt'
with open(p1, 'r') as f:
    c = f.read()

old = '''    val userAgent: String,
    val xUserAgent: String
)'''
new = '''    val userAgent: String,
    val xUserAgent: String,
    val getProfileEnabled: Boolean = true
)'''

if 'getProfileEnabled' not in c:
    c = c.replace(old, new, 1)
    with open(p1, 'w') as f:
        f.write(c)
    print('1. StalkerDeviceProfile: added getProfileEnabled')

# ═══ 2. OkHttpStalkerApiService.kt - conditional get_profile + pass toggle ═══
p2 = 'data/src/main/java/com/kuqforza/data/remote/stalker/OkHttpStalkerApiService.kt'
with open(p2, 'r') as f:
    c2 = f.read()

# 2a. Wrap 3 get_profile calls in condition
old_auth = '''            // get_profile #1: SN only, device_id/device_id2/signature EMPTY'''
new_auth = '''            if (profile.getProfileEnabled) {
            // get_profile #1: SN only, device_id/device_id2/signature EMPTY'''

if old_auth in c2 and 'if (profile.getProfileEnabled)' not in c2:
    c2 = c2.replace(old_auth, new_auth, 1)
    print('2a. Added if getProfileEnabled')

# Close the if block after get_main_info
old_return = '''            return Result.success(session to infoPayload.toProviderProfile())'''
new_return = '''            return Result.success(session to infoPayload.toProviderProfile())
            } else {
                // Skip get_profile - just get account info
                val infoResult2 = runCatching {
                    requestJson(
                        url = loadUrl, profile = profile, referer = referer, token = token,
                        query = mapOf("type" to "account_info", "action" to "get_main_info", "JsHttpRequest" to "1-xml")
                    )
                }
                val info2 = infoResult2.getOrElse { error -> lastError = error; continue }
                return Result.success(session to info2.toProviderProfile())
            }'''

if '} else {' not in c2 or 'Skip get_profile' not in c2:
    c2 = c2.replace(old_return, new_return, 1)
    print('2b. Added else branch for skip')

# 2c. Add getProfileEnabled to buildStalkerDeviceProfile
old_build = '''internal fun buildStalkerDeviceProfile(
    portalUrl: String,
    macAddress: String,
    deviceProfile: String,
    timezone: String,
    locale: String
): StalkerDeviceProfile {'''

new_build = '''internal fun buildStalkerDeviceProfile(
    portalUrl: String,
    macAddress: String,
    deviceProfile: String,
    timezone: String,
    locale: String,
    getProfileEnabled: Boolean = true
): StalkerDeviceProfile {'''

if 'getProfileEnabled: Boolean' not in c2:
    c2 = c2.replace(old_build, new_build, 1)
    print('2c. Added getProfileEnabled param')

# 2d. Pass getProfileEnabled to return
old_ret = '        xUserAgent = "Model: MAG250; Link: Ethernet,WiFi"'
new_ret = '''        xUserAgent = "Model: MAG250; Link: Ethernet,WiFi",
        getProfileEnabled = getProfileEnabled'''
if 'getProfileEnabled = getProfileEnabled' not in c2:
    c2 = c2.replace(old_ret, new_ret, 1)
    print('2d. Passed toggle to profile')

with open(p2, 'w') as f:
    f.write(c2)

# ═══ 3. Entity - add column ═══
p3 = 'data/src/main/java/com/kuqforza/data/local/entity/Entities.kt'
with open(p3, 'r') as f:
    c3 = f.read()

old3 = '    @ColumnInfo(name = "stalker_device_locale") val stalkerDeviceLocale: String = "",'
new3 = '''    @ColumnInfo(name = "stalker_device_locale") val stalkerDeviceLocale: String = "",
    @ColumnInfo(name = "stalker_get_profile", defaultValue = "1") val stalkerGetProfile: Boolean = true,'''

if 'stalkerGetProfile' not in c3:
    c3 = c3.replace(old3, new3, 1)
    with open(p3, 'w') as f:
        f.write(c3)
    print('3. Entity: added column')

# ═══ 4. Domain model - add field ═══
p4 = 'domain/src/main/java/com/kuqforza/domain/model/Provider.kt'
with open(p4, 'r') as f:
    c4 = f.read()

old4 = '    val stalkerDeviceLocale: String = "",'
new4 = '''    val stalkerDeviceLocale: String = "",
    val stalkerGetProfile: Boolean = true,'''

if 'stalkerGetProfile' not in c4:
    c4 = c4.replace(old4, new4, 1)
    with open(p4, 'w') as f:
        f.write(c4)
    print('4. Domain: added field')

# ═══ 5. Mapper ═══
p5 = 'data/src/main/java/com/kuqforza/data/mapper/EntityMappers.kt'
with open(p5, 'r') as f:
    c5 = f.read()

if 'stalkerGetProfile' not in c5:
    # Find stalkerDeviceLocale mappings and add after each
    c5 = c5.replace(
        'stalkerDeviceLocale = stalkerDeviceLocale,',
        'stalkerDeviceLocale = stalkerDeviceLocale,\n        stalkerGetProfile = stalkerGetProfile,',
        2  # Replace both occurrences (entity->domain and domain->entity)
    )
    with open(p5, 'w') as f:
        f.write(c5)
    print('5. Mapper: added')

# ═══ 6. Database migration ═══
p6 = 'data/src/main/java/com/kuqforza/data/local/KuqforzaDatabase.kt'
with open(p6, 'r') as f:
    c6 = f.read()

if 'stalker_get_profile' not in c6:
    import re
    m = re.search(r'version\s*=\s*(\d+)', c6)
    if m:
        old_ver = int(m.group(1))
        new_ver = old_ver + 1
        c6 = c6.replace(f'version = {old_ver}', f'version = {new_ver}')
        if 'fallbackToDestructiveMigration' not in c6:
            c6 = c6.replace('.build()', '.fallbackToDestructiveMigration()\n            .build()')
        with open(p6, 'w') as f:
            f.write(c6)
        print(f'6. Database: v{old_ver} -> v{new_ver}')

# ═══ 7. StalkerProvider - pass toggle ═══
p7 = 'data/src/main/java/com/kuqforza/data/remote/stalker/StalkerProvider.kt'
with open(p7, 'r') as f:
    c7 = f.read()

old7 = '    private val locale: String'
new7 = '''    private val locale: String,
    private val getProfileEnabled: Boolean = true'''

if 'getProfileEnabled' not in c7:
    c7 = c7.replace(old7, new7, 1)
    
    # Pass to buildStalkerDeviceProfile calls
    c7 = c7.replace(
        'normalizedLocale()\n        )',
        'normalizedLocale(),\n            getProfileEnabled = getProfileEnabled\n        )',
        2
    )
    with open(p7, 'w') as f:
        f.write(c7)
    print('7. StalkerProvider: pass toggle')

print('\nDone!')
