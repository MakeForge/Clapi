package io.github.makeforge.clapi.ui.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.i18n.I18nProvider
import io.github.makeforge.clapi.ui.shared.i18n.LocalI18n
import io.github.makeforge.clapi.ui.shared.state.AppTheme

/**
 * Accessor object – every property reads from the active [LocalAppColors] palette.
 * Must only be accessed from a @Composable context.
 */
object ClapiColors {
    val Background: Color          @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
    val Surface: Color             @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
    val SurfaceVariant: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceVariant
    val SurfaceContainer: Color    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceContainer
    val SurfaceHigh: Color         @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceHigh
    val Border: Color              @Composable @ReadOnlyComposable get() = LocalAppColors.current.border
    val BorderLight: Color         @Composable @ReadOnlyComposable get() = LocalAppColors.current.borderLight

    val Primary: Color             @Composable @ReadOnlyComposable get() = LocalAppColors.current.primary
    val PrimaryContainer: Color    @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryContainer
    val OnPrimary: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.onPrimary

    val Secondary: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.secondary
    val SecondaryContainer: Color  @Composable @ReadOnlyComposable get() = LocalAppColors.current.secondaryContainer
    val Tertiary: Color            @Composable @ReadOnlyComposable get() = LocalAppColors.current.tertiary

    val Error: Color               @Composable @ReadOnlyComposable get() = LocalAppColors.current.error

    val OnBackground: Color        @Composable @ReadOnlyComposable get() = LocalAppColors.current.onBackground
    val OnSurface: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.onSurface
    val OnSurfaceVariant: Color    @Composable @ReadOnlyComposable get() = LocalAppColors.current.onSurfaceVariant
    val OnSurfaceDim: Color        @Composable @ReadOnlyComposable get() = LocalAppColors.current.onSurfaceDim

    val MethodGet: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodGet
    val MethodPost: Color          @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodPost
    val MethodPut: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodPut
    val MethodPatch: Color         @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodPatch
    val MethodDelete: Color        @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodDelete
    val MethodOptions: Color       @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodOptions
    val MethodHead: Color          @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodHead
    val MethodTrace: Color         @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodTrace
    val MethodConnect: Color       @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodConnect
    val MethodWebSocket: Color     @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodWebSocket
    val MethodGraphQl: Color       @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodGraphQl
    val MethodRabbitMq: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodRabbitMq
    val BadgeHttp: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.badgeHttp
    val BadgeGrpc: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.badgeGrpc
    val BadgeMcp: Color            @Composable @ReadOnlyComposable get() = LocalAppColors.current.badgeMcp

    val StatusSuccess: Color       @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusSuccess
    val StatusRedirect: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusRedirect
    val StatusClientError: Color   @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusClientError
    val StatusServerError: Color   @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusServerError
    val StatusWarning: Color       @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusWarning
    val StatusInfo: Color          @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusInfo

    val HoverOverlay: Color        @Composable @ReadOnlyComposable get() = LocalAppColors.current.hoverOverlay
    val SelectedItem: Color        @Composable @ReadOnlyComposable get() = LocalAppColors.current.selectedItem
    val ActiveBorder: Color        @Composable @ReadOnlyComposable get() = LocalAppColors.current.primary
}

@Composable
@ReadOnlyComposable
fun httpMethodColor(method: HttpMethodType): Color = when (method) {
    HttpMethodType.GET     -> ClapiColors.MethodGet
    HttpMethodType.POST    -> ClapiColors.MethodPost
    HttpMethodType.PUT     -> ClapiColors.MethodPut
    HttpMethodType.PATCH   -> ClapiColors.MethodPatch
    HttpMethodType.DELETE  -> ClapiColors.MethodDelete
    HttpMethodType.OPTIONS -> ClapiColors.MethodOptions
    HttpMethodType.HEAD    -> ClapiColors.MethodHead
    HttpMethodType.TRACE   -> ClapiColors.MethodTrace
    HttpMethodType.CONNECT -> ClapiColors.MethodConnect
}

@Composable
@ReadOnlyComposable
fun statusCodeColor(code: Int): Color = when {
    code == 0        -> ClapiColors.StatusSuccess
    code in 1..16    -> ClapiColors.StatusServerError
    code in 200..299 -> ClapiColors.StatusSuccess
    code in 300..399 -> ClapiColors.StatusRedirect
    code in 400..499 -> ClapiColors.StatusClientError
    code >= 500      -> ClapiColors.StatusServerError
    else             -> ClapiColors.OnSurfaceVariant
}

val CodeFontFamily = FontFamily.Monospace

private val ClapiTypography = Typography(
    titleLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp),
    bodyLarge   = TextStyle(fontSize = 14.sp),
    bodyMedium  = TextStyle(fontSize = 13.sp),
    bodySmall   = TextStyle(fontSize = 12.sp),
    labelLarge  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall  = TextStyle(fontSize = 11.sp),
)

/** Wraps [content] in Material3 + Clapi colour scheme based on [appTheme] and i18n [language]. */
@Composable
fun ClapiTheme(
    appTheme: AppTheme = AppTheme.DARK,
    language: AppLanguage = AppLanguage.EN,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val palette = when (appTheme) {
        AppTheme.DARK   -> DarkAppColors
        AppTheme.LIGHT  -> LightAppColors
        AppTheme.SYSTEM -> if (systemDark) DarkAppColors else LightAppColors
    }
    val colorScheme = if (palette === DarkAppColors) {
        darkColorScheme(
            primary            = palette.primary,
            onPrimary          = palette.onPrimary,
            primaryContainer   = palette.primaryContainer,
            secondary          = palette.secondary,
            secondaryContainer = palette.secondaryContainer,
            tertiary           = palette.tertiary,
            error              = palette.error,
            background         = palette.background,
            onBackground       = palette.onBackground,
            surface            = palette.surface,
            onSurface          = palette.onSurface,
            surfaceVariant     = palette.surfaceVariant,
            onSurfaceVariant   = palette.onSurfaceVariant,
            outline            = palette.border,
            outlineVariant     = palette.borderLight,
        )
    } else {
        lightColorScheme(
            primary            = palette.primary,
            onPrimary          = palette.onPrimary,
            primaryContainer   = palette.primaryContainer,
            secondary          = palette.secondary,
            secondaryContainer = palette.secondaryContainer,
            tertiary           = palette.tertiary,
            error              = palette.error,
            background         = palette.background,
            onBackground       = palette.onBackground,
            surface            = palette.surface,
            onSurface          = palette.onSurface,
            surfaceVariant     = palette.surfaceVariant,
            onSurfaceVariant   = palette.onSurfaceVariant,
            outline            = palette.border,
            outlineVariant     = palette.borderLight,
        )
    }

    val i18nProvider = remember(language) { I18nProvider(language) }

    CompositionLocalProvider(
        LocalAppColors provides palette,
        LocalI18n provides i18nProvider,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = ClapiTypography,
            content     = content,
        )
    }
}
