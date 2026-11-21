package io.github.makeforge.clapi.ui.shared.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * All colors used by the Clapi UI, switchable per theme.
 */
data class AppColorPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderLight: Color,
    /** Visible-but-subtle guides for code indentation and folded regions. */
    val editorIndentGuide: Color,
    /** Fill and outline for the matching bracket pair in code editors. */
    val editorBracketMatchBackground: Color,
    val editorBracketMatchBorder: Color,

    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,

    val secondary: Color,
    val secondaryContainer: Color,
    val tertiary: Color,

    val error: Color,

    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onSurfaceDim: Color,

    val methodGet: Color,
    val methodPost: Color,
    val methodPut: Color,
    val methodPatch: Color,
    val methodDelete: Color,
    val methodOptions: Color,
    val methodHead: Color,
    val methodTrace: Color,
    val methodConnect: Color,
    val methodWebSocket: Color = Color(0xFF6366F1),
    val methodGraphQl: Color = Color(0xFFE10098),
    val methodRabbitMq: Color = Color(0xFFFF6600),
    val badgeHttp: Color = Color(0xFF22C55E),
    val badgeGrpc: Color = Color(0xFF06B6D4),
    val badgeMcp: Color = Color(0xFF14B8A6),

    val statusSuccess: Color,
    val statusRedirect: Color,
    val statusClientError: Color,
    val statusServerError: Color,
    val statusWarning: Color = Color(0xFFEAB308),
    val statusInfo: Color = Color(0xFF3B82F6),

    val hoverOverlay: Color,
    val selectedItem: Color,
)

val DarkAppColors = AppColorPalette(
    // Traditional dark background & layered surface hierarchy (deep neutral grays)
    background        = Color(0xFF121214),   // Deep, clean near-black background
    surface           = Color(0xFF18181B),   // Card / panel surface
    surfaceVariant    = Color(0xFF202024),   // Elevated inputs / toolbars
    surfaceContainer  = Color(0xFF27272A),   // Hover / active containers
    surfaceHigh       = Color(0xFF3F3F46),   // High emphasis borders / dividers
    border            = Color(0xFF27272A),   // Clean, subtle borders
    borderLight       = Color(0xFF3F3F46),   // Secondary crisp separator
    editorIndentGuide = Color(0xFF3F3F46),   // Subtle guide lines
    editorBracketMatchBackground = Color(0x3A3A7280), // Balanced subtle highlight
    editorBracketMatchBorder = Color(0xFF3A7BD5),     // Icon-aligned blue border

    // Primary — Aligned with the icon gradient (#3A7BD5 to #20235C)
    primary           = Color(0xFF3A7BD5),   // Vibrant icon blue
    primaryContainer  = Color(0xFF1E335E),   // Deep, readable container tint
    onPrimary         = Color(0xFFFFFFFF),

    // Secondary — Fresh mint teal (keeps great contrast)
    secondary          = Color(0xFF4EC9B0),
    secondaryContainer = Color(0xFF1A3D36),
    tertiary           = Color(0xFFE5C07B),   // Warm amber

    error              = Color(0xFFE06C75),   // Soft error red

    // Text hierarchy — neutral, clean whites and grays
    onBackground      = Color(0xFFF4F4F5),   // Primary readable white
    onSurface         = Color(0xFFE4E4E7),   // Surface text
    onSurfaceVariant  = Color(0xFFA1A1AA),   // Secondary labels / icons
    onSurfaceDim      = Color(0xFF71717A),   // Placeholder / hint text

    // HTTP Methods (High legibility on dark background)
    methodGet     = Color(0xFF4EC9B0),
    methodPost    = Color(0xFFE5C07B),
    methodPut     = Color(0xFF61AFEF),
    methodPatch   = Color(0xFFC678DD),
    methodDelete  = Color(0xFFE06C75),
    methodOptions = Color(0xFFA1A1AA),
    methodHead    = Color(0xFFA1A1AA),
    methodTrace   = Color(0xFF56B6C2),
    methodConnect = Color(0xFF56B6C2),
    methodWebSocket = Color(0xFF6366F1),
    methodGraphQl   = Color(0xFFE10098),
    methodRabbitMq  = Color(0xFFFF6600),
    badgeHttp       = Color(0xFF22C55E),
    badgeGrpc       = Color(0xFF06B6D4),
    badgeMcp        = Color(0xFF14B8A6),

    // Status
    statusSuccess     = Color(0xFF4EC9B0),
    statusRedirect    = Color(0xFFE5C07B),
    statusClientError = Color(0xFFE5C07B),
    statusServerError = Color(0xFFE06C75),

    hoverOverlay = Color(0x14FFFFFF),   // 8% white for clear hover states
    selectedItem = Color(0x263A7BD5),   // 15% primary tint for active selections
)
val LightAppColors = AppColorPalette(
    // Pure airy whites with the faintest blue-sky tint
    background        = Color(0xFFF8FAFC),   // almost pure white, whisper of sky
    surface           = Color(0xFFFFFFFF),   // pure white cards
    surfaceVariant    = Color(0xFFF1F5F9),   // barely-there cool tint
    surfaceContainer  = Color(0xFFE9EEF5),   // very light blue-gray
    surfaceHigh       = Color(0xFFDDE4EE),   // soft cool divider
    border            = Color(0xFFCED6E3),   // light, airy border
    borderLight       = Color(0xFFE2E8F2),   // near-invisible separator
    editorIndentGuide = Color(0xFF94A3B8),   // visible slate guide; quieter than text
    editorBracketMatchBackground = Color(0x0F4B6BF5), // barely-there blue wash; keeps punctuation crisp
    editorBracketMatchBorder = Color(0x604B6BF5),     // quiet outline for the matched delimiter

    // Primary — vivid but not heavy, sky-leaning blue
    primary           = Color(0xFF4B6BF5),   // bright periwinkle-blue
    primaryContainer  = Color(0xFFEAEEFF),   // airy lavender tint
    onPrimary         = Color(0xFFFFFFFF),

    // Secondary — fresh mint teal
    secondary          = Color(0xFF0FA88E),   // bright, light teal
    secondaryContainer = Color(0xFFDEF7F3),   // very light mint wash

    tertiary           = Color(0xFFB07D10),   // warm amber, lighter
    error              = Color(0xFFCF3141),   // lighter, less harsh red

    // Text — deep but not black; feels lighter overall
    onBackground      = Color(0xFF1A2030),   // deep navy-black
    onSurface         = Color(0xFF1E2535),   // slightly lighter
    onSurfaceVariant  = Color(0xFF5A657A),   // medium blue-gray
    onSurfaceDim      = Color(0xFF96A0B4),   // light, airy hint text

    // HTTP Methods — brighter, lighter variants
    methodGet     = Color(0xFF0FA88E),       // fresh teal
    methodPost    = Color(0xFFB07D10),       // warm amber
    methodPut     = Color(0xFF4B6BF5),       // periwinkle
    methodPatch   = Color(0xFF8B44CC),       // lighter purple
    methodDelete  = Color(0xFFCF3141),       // lighter red
    methodOptions = Color(0xFF6E7A91),       // cool slate
    methodHead    = Color(0xFF6E7A91),
    methodTrace   = Color(0xFF2A8FA3),       // teal-cyan
    methodConnect = Color(0xFF2A8FA3),
    methodWebSocket = Color(0xFF6366F1),
    methodGraphQl   = Color(0xFFE10098),
    methodRabbitMq  = Color(0xFFFF6600),
    badgeHttp       = Color(0xFF22C55E),
    badgeGrpc       = Color(0xFF06B6D4),
    badgeMcp        = Color(0xFF14B8A6),

    // Status
    statusSuccess     = Color(0xFF0FA88E),
    statusRedirect    = Color(0xFFB07D10),
    statusClientError = Color(0xFFB07D10),
    statusServerError = Color(0xFFCF3141),

    hoverOverlay = Color(0x094B6BF5),        // 3.5% primary tint on hover
    selectedItem = Color(0x154B6BF5),        // 8% primary for selection
)

/** Composition local providing the active [AppColorPalette]. Defaults to dark. */
val LocalAppColors = compositionLocalOf<AppColorPalette> { DarkAppColors }
