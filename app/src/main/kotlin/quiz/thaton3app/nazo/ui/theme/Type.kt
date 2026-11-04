package quiz.thaton3app.nazo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.R

// The mockups (v0.dev) use a rounded geometric sans we don't have the exact file for.
// Plus Jakarta Sans is the closest free match on Google Fonts.
//
// SETUP NEEDED: download the static .ttf files from
// https://fonts.google.com/specimen/Plus+Jakarta+Sans and place them at
// app/src/main/res/font/ using exactly these filenames:
//   plus_jakarta_sans_regular.ttf
//   plus_jakarta_sans_medium.ttf
//   plus_jakarta_sans_semibold.ttf
//   plus_jakarta_sans_bold.ttf
// (res/font requires lowercase_with_underscores filenames — Android will reject
// anything else.)

val NazoFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

val NazoTypography = Typography(
    // Large display: unused today, defined so any future use stays on-brand
    // instead of silently falling back to the platform default (Roboto).
    displayLarge = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle( // "Ready to test your anime knowledge?"
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle( // header title ("Nazo")
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
    ),
    titleMedium = TextStyle( // future section/card titles
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle( // input text, button labels
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle( // pill labels, badge text, nav labels
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle( // helper / caption text under fields
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Material buttons, chips and tabs render labelLarge — defining it here
    // is what keeps every button label on Plus Jakarta Sans instead of the
    // platform default font.
    labelLarge = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle( // "TOPIC" / "DIFFICULTY" / "QUESTIONS" captions
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
)
