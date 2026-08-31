package quiz.thaton3app.nazo.ui.screens

import quiz.thaton3app.nazo.ui.components.rememberHapticBack

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.achievements.Achievement
import quiz.thaton3app.nazo.achievements.AchievementsCard
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoDarkCard
import quiz.thaton3app.nazo.ui.theme.NazoDarkCardAccent
import quiz.thaton3app.nazo.ui.theme.NazoDarkCardTrack
import quiz.thaton3app.nazo.ui.theme.NazoOnDarkCard
import quiz.thaton3app.nazo.ui.theme.NazoOnDarkCardMuted
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoStatsCardBg
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import quiz.thaton3app.nazo.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DifficultyStat(val label: String, val played: Int, val accuracyPercent: Int)

private data class MasteredAnimeStat(val rank: Int, val title: String, val answers: Int, val avgScore: Int)

private data class StatsData(
    val level: Int,
    val currentXp: Int,
    val xpForNextLevel: Int,
    val totalQuizzes: Int,
    val overallAccuracyPercent: Int,
    val currentStreakDays: Int,
    val totalXp: Int,
    val bestTopic: String?, 
    val difficulty: List<DifficultyStat>,
    val topAnime: List<MasteredAnimeStat>,
)

private fun QuizStats.toStatsData(bonusXp: Int = 0): StatsData {
    val overallAccuracyPercent = if (totalQuestionsAnswered > 0) {
        totalCorrect * 100 / totalQuestionsAnswered
    } else {
        0
    }

    val difficultyOrder = listOf("Easy", "Medium", "Hard", "Otaku Master")
    val difficulty = difficultyOrder.map { label ->
        val played = difficultyPlays[label] ?: 0
        val answered = difficultyAnswered[label] ?: 0
        val correct = difficultyCorrect[label] ?: 0
        val accuracyPercent = if (answered > 0) correct * 100 / answered else 0
        DifficultyStat(label, played, accuracyPercent)
    }

    val ranked = animeAnswered.mapNotNull { (anime, answered) ->
        val correct = animeCorrect[anime] ?: 0
        if (answered <= 0) null else AnimeAcc(anime, answered, correct * 100 / answered)
    }.sortedWith(
        compareByDescending<AnimeAcc> { it.avgScore }.thenByDescending { it.answered }
    ).take(3)

    val topAnime = ranked.mapIndexed { index, e ->
        MasteredAnimeStat(index + 1, e.anime, e.answered, e.avgScore)
    }
    val bestTopic = topAnime.firstOrNull()?.title

    // Daily-challenge bonus XP is added on top of the stats-derived XP, so
    // dailies push the level forward without distorting any recorded stat.
    val xp = totalCorrect * 10 + totalQuizzes * 5 + bonusXp
    val xpForNextLevel = 200
    val level = (xp / xpForNextLevel) + 1
    val currentXp = xp % xpForNextLevel

    return StatsData(
        level = level,
        currentXp = currentXp,
        xpForNextLevel = xpForNextLevel,
        totalQuizzes = totalQuizzes,
        overallAccuracyPercent = overallAccuracyPercent,
        currentStreakDays = currentStreakDays,
        totalXp = xp,
        bestTopic = bestTopic,
        difficulty = difficulty,
        topAnime = if (topAnime.isEmpty()) {
            listOf(
                MasteredAnimeStat(1, "—", 0, 0),
                MasteredAnimeStat(2, "—", 0, 0),
                MasteredAnimeStat(3, "—", 0, 0),
            )
        } else {
            topAnime
        },
    )
}

private data class AnimeAcc(val anime: String, val answered: Int, val avgScore: Int)

@Composable
fun StatisticsScreen(
    stats: QuizStats = QuizStats(),
    bonusXp: Int = 0,
    achievements: List<Achievement> = emptyList(),
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    val data = remember(stats, bonusXp) { stats.toStatsData(bonusXp) }
    
    // Hoist the animation state to the screen level so it only plays once per visit
    var playAnimations by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { 
        // A tiny delay ensures the screen slide-in transition finishes before counting starts
        kotlinx.coroutines.delay(150)
        playAnimations = true 
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Swapped Column with verticalScroll to LazyColumn for lazy visibility animation triggering
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 12.dp)
        ) {
            item {
                ScreenHeader(title = "Statistics & Insights", onBackClick = onBackClick)
                Spacer(Modifier.height(20.dp))
            }
            item {
                RankCard(data, animate = playAnimations)
                Spacer(Modifier.height(16.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NumberStatTile(
                        icon = Icons.Filled.DoneAll,
                        label = "Total Games",
                        targetValue = data.totalQuizzes,
                        valueFormatter = { it.toString() },
                        subtitle = "Quizzes + guessing",
                        modifier = Modifier.weight(1f),
                        animate = playAnimations,
                    )
                    NumberStatTile(
                        icon = Icons.Filled.Speed,
                        label = "Overall Accuracy",
                        targetValue = data.overallAccuracyPercent,
                        valueFormatter = { "$it%" },
                        subtitle = "Correct answers",
                        modifier = Modifier.weight(1f),
                        animate = playAnimations,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NumberStatTile(
                        icon = Icons.Filled.LocalFireDepartment,
                        label = "Current Streak",
                        targetValue = data.currentStreakDays,
                        valueFormatter = { "$it Days" },
                        subtitle = "Keep it burning",
                        modifier = Modifier.weight(1f),
                        animate = playAnimations,
                    )
                    // Regular StatTile for String-based value that doesn't count up
                    StatTile(
                        icon = Icons.Filled.TrackChanges,
                        label = "Best Topic",
                        value = data.bestTopic ?: "—",
                        subtitle = if (data.bestTopic == null) "No games yet" else "Top mastered anime",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                DifficultyCard(data.difficulty, animate = playAnimations)
                Spacer(Modifier.height(16.dp))
            }
            item {
                TopAnimeCard(data.topAnime, animate = playAnimations)
                Spacer(Modifier.height(16.dp))
            }
            // Achievements (Phase 5): badge wall computed from already-persisted
            // data; hidden entirely when the caller passes no achievements.
            if (achievements.isNotEmpty()) {
                item {
                    AchievementsCard(achievements, animate = playAnimations)
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                item { Spacer(Modifier.height(8.dp)) }
            }
            item {
                ShareButton(data)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBackClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = rememberHapticBack(onBackClick),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NazoSurface),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NazoTextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RankCard(stats: StatsData, animate: Boolean) {
    val animatedXp by animateIntAsState(
        targetValue = if (animate) stats.currentXp else 0,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "animatedXp"
    )

    val targetProgress = if (stats.xpForNextLevel > 0) {
        (stats.currentXp.toFloat() / stats.xpForNextLevel).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = if (animate) targetProgress else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoDarkCard)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NazoDarkCardAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = NazoOnDarkCard, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CURRENT RANK",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoOnDarkCardMuted,
                )
                Text(
                    text = "Level ${stats.level} Otaku",
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoOnDarkCard,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(Icons.Filled.StarBorder, contentDescription = null, tint = NazoOnDarkCardMuted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(NazoDarkCardTrack)
        ) {
            Box(
                modifier = Modifier
                    // coerceAtLeast(0.001f) prevents "fraction must be greater than 0" crash on exact 0f
                    .fillMaxWidth(animatedProgress.coerceAtLeast(0.001f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NazoOnDarkCard)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$animatedXp / ${stats.xpForNextLevel} XP",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoOnDarkCardMuted,
            )
            Text(
                text = "${stats.xpForNextLevel - animatedXp} XP to Level ${stats.level + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoOnDarkCardMuted,
            )
        }
    }
}

// Dedicated composable for tiles with number values that we want to count up dynamically
@Composable
private fun NumberStatTile(
    icon: ImageVector,
    label: String,
    targetValue: Int,
    valueFormatter: (Int) -> String,
    subtitle: String,
    modifier: Modifier = Modifier,
    animate: Boolean,
) {
    val animatedValue by animateIntAsState(
        targetValue = if (animate) targetValue else 0,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = label
    )

    StatTile(
        icon = icon,
        label = label,
        value = valueFormatter(animatedValue),
        subtitle = subtitle,
        modifier = modifier
    )
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NazoStatsCardBg)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NazoSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.basicMarquee(repeatDelayMillis = 0),
        )
        if (subtitle.isNotEmpty()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun DifficultyCard(rows: List<DifficultyStat>, animate: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoStatsCardBg)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Games by Difficulty",
            style = MaterialTheme.typography.titleMedium,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        rows.forEachIndexed { index, row ->
            DifficultyRow(row, animate)
            if (index != rows.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DifficultyRow(row: DifficultyStat, animate: Boolean) {
    val animatedPercent by animateIntAsState(
        targetValue = if (animate) row.accuracyPercent else 0,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "diffPercent"
    )

    val animatedWidth by animateFloatAsState(
        targetValue = if (animate) (row.accuracyPercent / 100f) else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "diffWidth"
    )

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = row.label, style = MaterialTheme.typography.bodyLarge, color = NazoTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(text = "${row.played} played", style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NazoSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedWidth.coerceAtLeast(0.001f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(NazoDarkCard)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${animatedPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TopAnimeCard(rows: List<MasteredAnimeStat>, animate: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoStatsCardBg)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top Mastered Anime",
                style = MaterialTheme.typography.titleMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Filled.AutoAwesome, 
                contentDescription = null, 
                tint = NazoTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        rows.forEachIndexed { index, row ->
            TopAnimeRow(row, animate)
            if (index != rows.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopAnimeRow(row: MasteredAnimeStat, animate: Boolean) {
    val animatedScore by animateIntAsState(
        targetValue = if (animate) row.avgScore else 0,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "animeScore"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NazoPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = NazoOnPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${row.answers} Answers",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary
            )
        }
        
        Spacer(Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${animatedScore}% Avg",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ShareButton(data: StatsData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            scope.launch(Dispatchers.IO) {
                try {
                    val file = shareBitmap(data, context)
                    val uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        context.startActivity(
                            Intent.createChooser(intent, "Share your Nazo stats"),
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NazoPrimary,
            contentColor = NazoOnPrimary
        ),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = "Share My Stats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun shareBitmap(data: StatsData, context: Context): File {
    val W = 1080
    val H = 1440 
    val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgTop = 0xFFF3FCF4.toInt()
    val bgBottom = 0xFFE6F6EA.toInt()
    val card = 0xFF163A2A.toInt()        
    val onCard = 0xFFFFFFFF.toInt()      
    val onMuted = 0xFFA9C9B6.toInt()     
    val mint = 0xFFC5E5D4.toInt()        
    val darkOnMint = card                
    val innerCard = 0xFF214C39.toInt()   
    val fabBg = 0xFF214C39.toInt()       

    val fontBold = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold)
    val fontReg = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_regular)
    val fontSemi = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_semibold)

    fun textCentered(text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, baseline, paint)
    }

    val bgPaint = Paint().apply { isAntiAlias = true }
    bgPaint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), bgTop, bgBottom, Shader.TileMode.CLAMP)
    canvas.drawPaint(bgPaint)

    val cardLeft = 48f
    val cardTop = 48f
    val cardRight = (W - 48).toFloat()
    val cardBottom = (H - 48).toFloat()
    val radius = 72f

    canvas.drawRoundRect(
        cardLeft, cardTop + 30f, cardRight, cardBottom + 30f, radius, radius,
        Paint().apply { color = 0x35000000.toInt(); isAntiAlias = true }
    )

    canvas.drawRoundRect(
        cardLeft, cardTop, cardRight, cardBottom, radius, radius,
        Paint().apply { color = card; isAntiAlias = true }
    )

    val pad = 64f 

    canvas.save()
    val clip = Path()
    clip.addRoundRect(cardLeft, cardTop, cardRight, cardBottom, radius, radius, Path.Direction.CW)
    canvas.clipPath(clip)

    val accentPaint = Paint().apply {
        isAntiAlias = true
        color = 0x0FFFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 64f
    }
    fun cornerAccent(cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, 130f, accentPaint)
        canvas.drawCircle(cx, cy, 210f, accentPaint)
    }
    cornerAccent(cardRight, cardTop)
    cornerAccent(cardLeft, cardBottom)

    canvas.drawText(
        "Nazo", cardLeft + pad, cardTop + pad + 16f,
        Paint().apply { color = onCard; textSize = 64f; typeface = fontBold; isAntiAlias = true }
    )
    canvas.drawText(
        "MY ANIME STATS", cardLeft + pad, cardTop + pad + 54f,
        Paint().apply { color = onMuted; textSize = 22f; typeface = fontSemi; isAntiAlias = true; letterSpacing = 0.15f }
    )

    val fabCx = cardRight - pad - 20f
    val fabCy = cardTop + pad + 20f
    canvas.drawCircle(fabCx, fabCy, 46f, Paint().apply { color = fabBg; isAntiAlias = true })
    textCentered("✨", fabCx, fabCy, Paint().apply { textSize = 42f; isAntiAlias = true })

    val badgeCx = W / 2f
    val badgeCy = cardTop + 300f
    val badgeR = 135f
    canvas.drawCircle(badgeCx, badgeCy, badgeR + 10f, Paint().apply { color = 0x1AFFFFFF; isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 4f })
    canvas.drawCircle(badgeCx, badgeCy, badgeR, Paint().apply { color = mint; isAntiAlias = true })
    
    drawTrophy(canvas, badgeCx, badgeCy - 60f, 50f, darkOnMint)
    textCentered(
        data.level.toString(), badgeCx, badgeCy + 10f,
        Paint().apply { color = darkOnMint; textSize = 90f; typeface = fontBold; isAntiAlias = true }
    )
    textCentered(
        "LEVEL", badgeCx, badgeCy + 70f,
        Paint().apply { color = darkOnMint; textSize = 22f; typeface = fontSemi; isAntiAlias = true; letterSpacing = 0.2f }
    )

    textCentered(
        "Otaku in training", badgeCx, badgeCy + badgeR + 50f,
        Paint().apply { color = onCard; textSize = 44f; typeface = fontBold; isAntiAlias = true }
    )
    textCentered(
        String.format("%,d XP earned", data.totalXp), badgeCx, badgeCy + badgeR + 92f,
        Paint().apply { color = onMuted; textSize = 26f; typeface = fontReg; isAntiAlias = true }
    )

    val gridTop = badgeCy + badgeR + 135f
    val gridH = 140f
    val gap = 20f
    val gridW = (cardRight - cardLeft - 2 * pad - 2 * gap) / 3f
    val chips = listOf(
        Pair(data.totalQuizzes.toString(), "GAMES"),
        Pair("${data.overallAccuracyPercent}%", "ACCURACY"),
        Pair("${data.currentStreakDays}", "DAY STREAK")
    )
    chips.forEachIndexed { i, (value, label) ->
        val x = cardLeft + pad + i * (gridW + gap)
        canvas.drawRoundRect(
            x, gridTop, x + gridW, gridTop + gridH, 28f, 28f,
            Paint().apply { color = innerCard; isAntiAlias = true }
        )
        textCentered(
            value, x + gridW / 2f, gridTop + 58f,
            Paint().apply { color = onCard; textSize = 44f; typeface = fontBold; isAntiAlias = true }
        )
        textCentered(
            label, x + gridW / 2f, gridTop + 106f,
            Paint().apply { color = onMuted; textSize = 17f; typeface = fontSemi; isAntiAlias = true; letterSpacing = 0.15f }
        )
    }

    val btY = gridTop + gridH + 65f
    canvas.drawText(
        "BEST TOPIC", cardLeft + pad, btY,
        Paint().apply { color = onMuted; textSize = 22f; typeface = fontSemi; isAntiAlias = true; letterSpacing = 0.15f }
    )
    val pillText = "${data.topAnime.firstOrNull()?.avgScore ?: 0}%"
    val pillPaint = Paint().apply { textSize = 26f; typeface = fontBold; isAntiAlias = true }
    val pillW = pillPaint.measureText(pillText) + 48f
    val pillX = cardRight - pad - pillW
    val pillH = 52f
    
    var btTitle = data.bestTopic ?: "—"
    val titlePaint = Paint().apply { textSize = 42f; typeface = fontBold; isAntiAlias = true; color = onCard }
    val maxTitleW = pillX - 24f - (cardLeft + pad)
    while (titlePaint.measureText(btTitle) > maxTitleW && btTitle.length > 1) {
        btTitle = btTitle.dropLast(1)
    }
    if (btTitle != (data.bestTopic ?: "—")) btTitle = btTitle.dropLast(1) + "…"
    
    canvas.drawText(btTitle, cardLeft + pad, btY + 52f, titlePaint)
    
    canvas.drawRoundRect(
        pillX, btY + 10f, pillX + pillW, btY + 10f + pillH, pillH / 2f, pillH / 2f,
        Paint().apply { color = mint; isAntiAlias = true }
    )
    textCentered(
        pillText, pillX + pillW / 2f, btY + 10f + pillH / 2f,
        Paint().apply { color = darkOnMint; textSize = 26f; typeface = fontBold; isAntiAlias = true }
    )

    val taY = btY + 130f
    drawFlame(canvas, cardLeft + pad + 10f, taY - 8f, 18f, onMuted)
    canvas.drawText(
        "TOP MASTERED ANIME", cardLeft + pad + 38f, taY,
        Paint().apply { color = onMuted; textSize = 22f; typeface = fontSemi; isAntiAlias = true; letterSpacing = 0.15f }
    )
    
    val listTop = taY + 52f
    data.topAnime.take(3).forEachIndexed { i, row ->
        val ry = listTop + i * 104f
        if (i > 0) {
            canvas.drawLine(
                cardLeft + pad, ry - 50f, cardRight - pad, ry - 50f,
                Paint().apply { color = 0x1AFFFFFF.toInt(); isAntiAlias = true; strokeWidth = 2f }
            )
        }
        textCentered(
            String.format("%02d", row.rank), cardLeft + pad + 10f, ry + 6f,
            Paint().apply { color = onCard; textSize = 30f; typeface = fontBold; isAntiAlias = true }
        )
        
        var rowTitle = row.title
        val rowTitlePaint = Paint().apply { textSize = 32f; typeface = fontBold; isAntiAlias = true; color = onCard }
        val maxRowW = cardRight - pad - (cardLeft + pad + 90f)
        while (rowTitlePaint.measureText(rowTitle) > maxRowW && rowTitle.length > 1) {
            rowTitle = rowTitle.dropLast(1)
        }
        if (rowTitle != row.title) rowTitle = rowTitle.dropLast(1) + "…"
        
        canvas.drawText(rowTitle, cardLeft + pad + 90f, ry - 4f, rowTitlePaint)
        canvas.drawText(
            "${row.answers} answers · ${row.avgScore}% avg", cardLeft + pad + 90f, ry + 32f,
            Paint().apply { color = onMuted; textSize = 22f; typeface = fontReg; isAntiAlias = true }
        )
    }

    canvas.restore()

    val footerY = cardBottom - 40f
    val footerPaint = Paint().apply {
        color = onMuted; textSize = 20f; typeface = fontReg; isAntiAlias = true; textAlign = Paint.Align.LEFT
    }
    canvas.drawText("Quiz. Learn. Level up.", cardLeft + pad, footerY, footerPaint)
    footerPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText("nazo.app", cardRight - pad, footerY, footerPaint)

    val dir = context.getExternalFilesDir(null) ?: context.filesDir
    val file = File(dir, "nazo_stats.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}

private fun drawTrophy(c: Canvas, cx: Float, cy: Float, h: Float, color: Int) {
    val p = Paint().apply { isAntiAlias = true; this.color = color }
    val topW = h * 0.46f
    val botW = h * 0.30f
    val top = cy - h * 0.5f
    val bot = cy + h * 0.08f
    val cup = Path().apply {
        moveTo(cx - topW, top)
        lineTo(cx + topW, top)
        lineTo(cx + botW, bot)
        lineTo(cx - botW, bot)
        close()
    }
    c.drawPath(cup, p)
    c.drawRect(cx - h * 0.07f, bot, cx + h * 0.07f, bot + h * 0.22f, p)
    c.drawRect(cx - h * 0.26f, bot + h * 0.22f, cx + h * 0.26f, bot + h * 0.32f, p)
    val hp = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = h * 0.07f }
    c.drawCircle(cx - topW + h * 0.04f, top + h * 0.26f, h * 0.16f, hp)
    c.drawCircle(cx + topW - h * 0.04f, top + h * 0.26f, h * 0.16f, hp)
}

private fun drawFlame(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
    val p = Paint().apply { isAntiAlias = true; this.color = color }
    val path = Path().apply {
        moveTo(cx, cy - s)
        quadTo(cx + s * 0.7f, cy - s * 0.1f, cx + s * 0.35f, cy + s * 0.7f)
        quadTo(cx + s * 0.15f, cy + s * 0.2f, cx, cy + s * 0.85f)
        quadTo(cx - s * 0.15f, cy + s * 0.2f, cx - s * 0.35f, cy + s * 0.7f)
        quadTo(cx - s * 0.7f, cy - s * 0.1f, cx, cy - s)
        close()
    }
    c.drawPath(path, p)
}

