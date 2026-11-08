package com.fitpdf.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private var docUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Let content draw into the camera cutout area too: no black strip at the top.
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        docUri = intent?.data
        setContent {
            FitPdfTheme {
                App(
                    uri = docUri,
                    onPicked = { docUri = it },
                    onClose = { docUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { docUri = it }
    }
}

// ───────────────────────────── Theme ─────────────────────────────

private val Indigo = Color(0xFF6366F1)
private val Ink = Color(0xFF0F172A)

@Composable
fun FitPdfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Indigo,
            secondary = Color(0xFFFDE047),
            background = Ink,
            surface = Color(0xFF1E293B),
        ),
        content = content,
    )
}

// ───────────────────────────── App shell ─────────────────────────────

@Composable
fun App(uri: Uri?, onPicked: (Uri) -> Unit, onClose: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) onPicked(picked)
    }
    val pick = { picker.launch(arrayOf("application/pdf")) }

    if (uri == null) {
        SystemBars(visible = true)
        HomeScreen(onOpen = pick)
    } else {
        ReaderScreen(uri = uri, onOpenOther = pick, onClose = onClose)
    }
}

@Composable
private fun SystemBars(visible: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

// ───────────────────────────── Home ─────────────────────────────

@Composable
fun HomeScreen(onOpen: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF312E81)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            FitLogo(Modifier.size(120.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tagline),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                modifier = Modifier.height(56.dp),
            ) {
                Text(
                    stringResource(R.string.open_pdf),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun FitLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        // page
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(s * 0.27f, s * 0.2f),
            size = Size(s * 0.46f, s * 0.6f),
            cornerRadius = CornerRadius(s * 0.05f),
        )
        for (i in 0..2) {
            drawRoundRect(
                color = Indigo,
                topLeft = Offset(s * 0.34f, s * (0.34f + i * 0.12f)),
                size = Size(if (i == 2) s * 0.2f else s * 0.32f, s * 0.03f),
                cornerRadius = CornerRadius(s * 0.015f),
            )
        }
        // corner brackets
        val stroke = Stroke(width = s * 0.04f, cap = StrokeCap.Round)
        val y = Color(0xFFFDE047)
        val a = s * 0.1f; val b = s * 0.9f; val l = s * 0.2f
        fun corner(x: Float, yy: Float, dx: Float, dy: Float) {
            drawLine(y, Offset(x, yy), Offset(x + dx * l, yy), stroke.width, StrokeCap.Round)
            drawLine(y, Offset(x, yy), Offset(x, yy + dy * l), stroke.width, StrokeCap.Round)
        }
        corner(a, a, 1f, 1f); corner(b, a, -1f, 1f)
        corner(a, b, 1f, -1f); corner(b, b, -1f, -1f)
    }
}

// ───────────────────────────── Reader ─────────────────────────────

private val ZOOM_LEVELS = listOf(1f, 1.5f, 2f, 3f)
private val INVERT = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

private sealed interface LoadState {
    data class Working(val done: Int, val total: Int) : LoadState
    data class Ready(val engine: PdfEngine, val analysis: Analysis) : LoadState
    data class NeedsPassword(val wrong: Boolean) : LoadState
    data class Failed(val messageRes: Int) : LoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(uri: Uri, onOpenOther: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current

    var load by remember(uri) { mutableStateOf<LoadState>(LoadState.Working(0, 1)) }
    var trim by rememberSaveable { mutableStateOf(true) }
    var night by rememberSaveable { mutableStateOf(false) }
    var zoomIndex by rememberSaveable { mutableStateOf(0) }
    var showBars by rememberSaveable { mutableStateOf(false) }

    // Open + analyze. The engine is closed when the screen leaves composition or the file changes.
    val holder = remember(uri) { arrayOfNulls<PdfEngine>(1) }
    DisposableEffect(uri) { onDispose { holder[0]?.close() } }
    var password by remember(uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(uri, password) {
        load = LoadState.Working(0, 1)
        try {
            val engine = PdfEngine.open(context, uri, password)
            holder[0] = engine
            val analysis = engine.analyze { done, total -> load = LoadState.Working(done, total) }
            load = LoadState.Ready(engine, analysis)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            load = if (t is SecurityException) {
                // Protected PDF: ask for the password (Android 15+), otherwise explain.
                if (Build.VERSION.SDK_INT >= 35) LoadState.NeedsPassword(wrong = password != null)
                else LoadState.Failed(R.string.error_password_old)
            } else {
                LoadState.Failed(R.string.error_open)
            }
        }
    }

    BackHandler { onClose() }
    // While reading, hide the status/navigation bars together with the overlay → truly full screen.
    SystemBars(visible = showBars || load !is LoadState.Ready)

    val pageBg = if (night) Color.Black else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .background(pageBg)
            .pointerInput(Unit) { detectTapGestures(onTap = { showBars = !showBars }) }
    ) {
        when (val s = load) {
            is LoadState.Working -> Column(
                Modifier.align(Alignment.Center).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Indigo)
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { s.done / s.total.toFloat() },
                    color = Indigo,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.analyzing, s.done, s.total),
                    color = if (night) Color.White else Color(0xFF334155),
                )
            }

            is LoadState.NeedsPassword -> PasswordDialog(
                wrong = s.wrong,
                onSubmit = { password = it },
                onCancel = onClose,
            )

            is LoadState.Failed -> Column(
                Modifier.align(Alignment.Center).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(s.messageRes),
                    color = if (night) Color.White else Color(0xFF334155),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            }

            is LoadState.Ready -> {
                val pages = remember(s, trim) {
                    // With trimming on, completely blank pages are skipped.
                    if (trim) s.analysis.pages.filter { it.content != null } else s.analysis.pages
                }
                val zoom = ZOOM_LEVELS[zoomIndex]
                val density = LocalDensity.current
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val renderWidthPx = with(density) { (screenWidthDp.dp.toPx() * zoom).toInt() }
                    .coerceIn(480, 2600)
                val listState = rememberLazyListState()

                Box(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // No contentPadding, no spacedBy → pages touch each other and the screen edges.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width((screenWidthDp * zoom).dp).fillMaxSize(),
                    ) {
                        itemsIndexed(pages, key = { _, p -> p.index }) { _, page ->
                            PageItem(
                                engine = s.engine,
                                analysis = s.analysis,
                                page = page,
                                trim = trim,
                                night = night,
                                renderWidthPx = renderWidthPx,
                            )
                        }
                    }
                }

                val current by remember(listState, pages) {
                    derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.size.coerceAtLeast(1)) }
                }
                // Small page counter, only visible with the overlay.
                AnimatedVisibility(
                    visible = showBars,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
                ) {
                    Text(
                        stringResource(R.string.page_counter, current, pages.size),
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                // Top bar
                AnimatedVisibility(
                    visible = showBars,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = onClose) { Text("✕  " + stringResource(R.string.close), color = Color.White) }
                        TextButton(onClick = onOpenOther) { Text(stringResource(R.string.open_another), color = Color.White) }
                    }
                }

                // Bottom bar
                AnimatedVisibility(
                    visible = showBars,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        val chipColors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0x33FFFFFF),
                            labelColor = Color.White,
                            selectedContainerColor = Indigo,
                            selectedLabelColor = Color.White,
                        )
                        FilterChip(
                            selected = trim, onClick = { trim = !trim },
                            label = { Text(stringResource(R.string.trim)) }, colors = chipColors,
                        )
                        FilterChip(
                            selected = night, onClick = { night = !night },
                            label = { Text(stringResource(R.string.night)) }, colors = chipColors,
                        )
                        FilterChip(
                            selected = zoomIndex > 0,
                            onClick = { zoomIndex = (zoomIndex + 1) % ZOOM_LEVELS.size },
                            label = {
                                Text(stringResource(R.string.zoom_fmt, ZOOM_LEVELS[zoomIndex].toString().removeSuffix(".0")))
                            },
                            colors = chipColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageItem(
    engine: PdfEngine,
    analysis: Analysis,
    page: PageInfo,
    trim: Boolean,
    night: Boolean,
    renderWidthPx: Int,
) {
    val crop = remember(page, trim, analysis) { PdfEngine.cropFor(page, analysis, trim) }
    // Reserve the exact final height before the bitmap is ready → no layout jumping while scrolling.
    val ratio = (crop.width() * page.widthPt) / (crop.height() * page.heightPt)

    var bitmap by remember(page.index, trim, renderWidthPx) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(page.index, trim, renderWidthPx) {
        bitmap = engine.render(page, crop, renderWidthPx)?.asImageBitmap()
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(if (night) Color.Black else Color.White)
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.High,
                colorFilter = if (night) INVERT else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.password_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.password_hint)) },
                isError = wrong,
                supportingText = if (wrong) {
                    { Text(stringResource(R.string.password_wrong)) }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = text.isNotEmpty()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}
