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
    headlineMedium = TextStyle( // "Ready to test your anime knowledge?"
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
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
    labelSmall = TextStyle( // "TOPIC" / "DIFFICULTY" / "QUESTIONS" captions
        fontFamily = NazoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
)
