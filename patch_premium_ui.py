#!/usr/bin/env python3
"""
Complete Kuqforza IPTV Premium UI overhaul:
1. AppColors - pure black + electric blue
2. AppShapes - more rounded, glass feel
3. Logo vector - KQ monogram
4. Launcher background
5. App name in strings
"""

import os
ROOT = os.path.expanduser("~/Forza113-Player")
os.chdir(ROOT)

# ═══ 1. AppColors - Pure black + electric blue premium ═══
colors_path = 'app/src/main/java/com/kuqforza/iptv/ui/design/AppColors.kt'
with open(colors_path, 'w') as f:
    f.write('''package com.kuqforza.iptv.ui.design

import androidx.compose.ui.graphics.Color

object AppColors {
    // ── Backgrounds: pure black base ──
    val Canvas          = Color(0xFF000000)
    val CanvasElevated  = Color(0xFF040810)
    val Surface         = Color(0xFF080E18)
    val SurfaceElevated = Color(0xFF0E1726)
    val SurfaceEmphasis = Color(0xFF152035)
    val SurfaceAccent   = Color(0xFF1B2A45)

    // ── Brand: electric blue ──
    val Brand       = Color(0xFF0088FF)
    val BrandMuted  = Color(0x330088FF)
    val BrandStrong = Color(0xFF55BBFF)
    val Focus       = Color(0xFFDDEEFF)

    // ── Text ──
    val TextPrimary   = Color(0xFFF0F4FA)
    val TextSecondary = Color(0xFF8899B0)
    val TextTertiary  = Color(0xFF5A6A82)
    val TextDisabled  = Color(0xFF3A4558)

    // ── Semantic ──
    val Live    = Color(0xFFFF4D58)
    val Success = Color(0xFF00E676)
    val Warning = Color(0xFFFFAB40)
    val Info    = Color(0xFF40C4FF)

    // ── Surfaces ──
    val Divider = Color(0x180088FF)
    val Outline = Color(0x260088FF)

    // ── Hero overlay ──
    val HeroTop    = Color(0xCC000000)
    val HeroBottom = Color(0xF2000000)
}
''')
print('1. AppColors - pure black + electric blue')

# ═══ 2. AppShapes - more glass, more premium ═══
shapes_path = 'app/src/main/java/com/kuqforza/iptv/ui/design/AppShapes.kt'
with open(shapes_path, 'w') as f:
    f.write('''package com.kuqforza.iptv.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

data class AppShapes(
    val xSmall: RoundedCornerShape = RoundedCornerShape(10.dp),
    val small:  RoundedCornerShape = RoundedCornerShape(14.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp),
    val large:  RoundedCornerShape = RoundedCornerShape(26.dp),
    val xLarge: RoundedCornerShape = RoundedCornerShape(32.dp),
    val pill:   RoundedCornerShape = RoundedCornerShape(999.dp)
)

val LocalAppShapes = staticCompositionLocalOf { AppShapes() }
''')
print('2. AppShapes - rounder, more premium')

# ═══ 3. Theme.kt - darker material theme ═══
theme_path = 'app/src/main/java/com/kuqforza/iptv/ui/theme/Theme.kt'
if os.path.exists(theme_path):
    with open(theme_path, 'r') as f:
        theme = f.read()
    # Make sure primary is our brand blue
    theme = theme.replace('primary = AppColors.Brand', 'primary = AppColors.Brand')
    with open(theme_path, 'w') as f:
        f.write(theme)
    print('3. Theme verified')

# ═══ 4. Logo - KQ monogram vector ═══
logo_path = 'app/src/main/res/drawable/ic_launcher_foreground.xml'
with open(logo_path, 'w') as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- BG circle -->
    <path
        android:fillColor="#000000"
        android:pathData="M0,0h108v108H0z"/>

    <!-- Outer ring glow -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#0088FF"
        android:strokeWidth="1.5"
        android:pathData="M54,54m-44,0a44,44 0,1,1 88,0a44,44 0,1,1 -88,0"/>

    <!-- Inner ring -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#55BBFF"
        android:strokeWidth="0.8"
        android:pathData="M54,54m-40,0a40,40 0,1,1 80,0a40,40 0,1,1 -80,0"/>

    <!-- K letter - big bold -->
    <path
        android:fillColor="#0088FF"
        android:pathData="M26,30 L26,78 L35,78 L35,59 L51,78 L63,78 L43,55 L61,30 L50,30 L35,51 L35,30 Z"/>

    <!-- Q letter - big bold outline -->
    <path
        android:fillColor="#55BBFF"
        android:pathData="M67,30 L67,30
        C80,30 88,40 88,54
        C88,66 82,74 74,78
        L82,86 L76,90 L67,80
        C65,80 63,80 61,78
        C54,74 50,66 50,54
        C50,40 58,30 67,30 Z
        M67,38
        C60,38 56,44 56,54
        C56,64 60,70 67,70
        C74,70 78,64 78,54
        C78,44 74,38 67,38 Z"/>

</vector>
''')
print('4. KQ logo vector created')

# ═══ 5. Launcher background - pure black ═══
bg_path = 'app/src/main/res/values/ic_launcher_background.xml'
with open(bg_path, 'w') as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#000000</color>
</resources>
''')
print('5. Launcher background black')

# ═══ 6. Fix app name ═══
strings_path = 'app/src/main/res/values/strings.xml'
if os.path.exists(strings_path):
    with open(strings_path, 'r') as f:
        s = f.read()
    s = s.replace('<string name="app_name">Kuqforza</string>',
                  '<string name="app_name">Kuqforza IPTV Premium</string>')
    s = s.replace('<string name="app_name">Kuqforza IPTV Premium IPTV Premium</string>',
                  '<string name="app_name">Kuqforza IPTV Premium</string>')
    with open(strings_path, 'w') as f:
        f.write(s)
    print('6. App name: Kuqforza IPTV Premium')

# ═══ 7. Fix FocusSpec for better focus glow ═══
focus_path = 'app/src/main/java/com/kuqforza/iptv/ui/design/FocusSpec.kt'
if os.path.exists(focus_path):
    with open(focus_path, 'r') as f:
        fc = f.read()
    # Make focus border thicker and brighter
    fc = fc.replace('width = 2.dp', 'width = 3.dp')
    fc = fc.replace('width = 1.dp', 'width = 2.dp')
    with open(focus_path, 'w') as f:
        f.write(fc)
    print('7. Focus glow enhanced')

# ═══ 8. Splash/setup screen branding ═══
# Find setup screen references
for dirpath, _, filenames in os.walk('app/src/main/java/com/kuqforza/iptv/ui'):
    for fname in filenames:
        if 'setup' in fname.lower() or 'splash' in fname.lower() or 'welcome' in fname.lower():
            fpath = os.path.join(dirpath, fname)
            with open(fpath, 'r') as f:
                content = f.read()
            # Replace any remaining StreamVault references
            if 'Kuqforza' in content and 'IPTV' not in content:
                content = content.replace('"Kuqforza"', '"Kuqforza IPTV Premium"')
            with open(fpath, 'w') as f:
                f.write(content)
            print(f'8. Updated: {fname}')

# ═══ 9. Color.kt - update mapped colors ═══
color_path = 'app/src/main/java/com/kuqforza/iptv/ui/theme/Color.kt'
if os.path.exists(color_path):
    with open(color_path, 'r') as f:
        ct = f.read()
    # Ensure gradient overlay is pure black
    if 'Color(0xCC' in ct:
        ct = ct.replace('Color(0xCC07111B)', 'Color(0xCC000000)')
        ct = ct.replace('Color(0xF207111B)', 'Color(0xF2000000)')
    with open(color_path, 'w') as f:
        f.write(ct)
    print('9. Color.kt gradients fixed')

# ═══ 10. Navigation/Bottom bar colors ═══
for dirpath, _, filenames in os.walk('app/src/main/java/com/kuqforza/iptv/ui'):
    for fname in filenames:
        if 'shell' in fname.lower() or 'navigation' in fname.lower() or 'sidebar' in fname.lower():
            fpath = os.path.join(dirpath, fname)
            try:
                with open(fpath, 'r') as f:
                    content = f.read()
                original = content
                # Make nav backgrounds darker
                content = content.replace('0xFF07111B', '0xFF000000')
                content = content.replace('0xFF0B1622', '0xFF040810')
                content = content.replace('0xFF0F1B29', '0xFF080E18')
                if content != original:
                    with open(fpath, 'w') as f:
                        f.write(content)
                    print(f'10. Darkened: {fname}')
            except: pass

print('\nDone! Push with: git add -A && git commit -m "premium UI overhaul" && git push')
