package com.auxo.pos.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auxo.pos.R
import com.auxo.pos.ui.components.AuxoArtwork
import com.auxo.pos.ui.components.Illustration
import com.auxo.pos.ui.theme.AuthCard
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthInkMuted

/*
 * THE THIRD ONBOARDING PAGE ("Choose what you do"), kept in this one file, built so its pictures can be added later.
 *
 * Like the second page, everything is laid out once, in dp, inside a fixed design frame, and the frame is scaled as a
 * whole to fit the screen (see IntroPage.kt). Every phone shows the same picture; only its overall size changes.
 *
 * ADDING PICTURES
 *   1. Put the file in app/src/main/res/drawable-nodpi (lowercase letters, digits and underscores only).
 *   2. In the PICTURES list just below, write R.drawable.<file name> in place of null.
 * A picture that is still null is simply not drawn, so the page works with none of them. The places and sizes are
 * the ones in the mock-up; when a design export arrives, replace the Art(...) lines in DecorationArt().
 *
 * What is native code, not a picture: the headline (with its green part), the line below it,
 * the six cards (white card, round icon button, label, arrow button). The six category
 * icons are soft-render emerald vector artwork (res/drawable/ic_cat_*.xml, drawn by tools/gen_category_icons.py), so they stay sharp at any size.
 */

// ---------------------------------------------- PICTURES ----------------------------------------------
/** Top left: the green bag, the plant and the white platform (about 178 x 160 dp). */
private val CornerBag: Int? = null
/** Top right: the till with its receipt, the plant and the platform (about 114 x 215 dp). */
private val CornerTill: Int? = null
/** The loose leaves scattered over the page, from the top down. */
private val LeafTopRight: Int? = null
private val LeafMiddle: Int? = null
private val LeafBlurLeft: Int? = null
private val LeafRight: Int? = null
/** Bottom left: the two big leaves in the corner (about 150 x 100 dp), a soft one beside the arrow, and one bottom right. */
private val LeavesBottomLeft: Int? = null
private val LeafBottomMiddle: Int? = null
private val LeafBottomRight: Int? = null

/** The six card pictures, each with its emerald category icon (res/drawable/ic_cat_*.xml) shown on a mint disc beside its label. */
private class CategoryCard(
    @DrawableRes val picture: Int,
    @StringRes val label: Int,
    @DrawableRes val icon: Int? = null,
)

/** Examples only: the shop's real categories are chosen after setup. */
private val CATEGORY_CARDS = listOf(
    CategoryCard(R.drawable.onb_apparel, R.string.onboarding_cat_apparel, R.drawable.ic_cat_apparel),
    CategoryCard(R.drawable.onb_burger, R.string.onboarding_cat_food, R.drawable.ic_cat_food),
    CategoryCard(R.drawable.onb_cat_grocery, R.string.onboarding_cat_grocery, R.drawable.ic_cat_grocery),
    CategoryCard(R.drawable.onb_cat_electronics, R.string.onboarding_cat_electronics, R.drawable.ic_cat_electronics),
    CategoryCard(R.drawable.onb_cat_beauty, R.string.onboarding_cat_beauty, R.drawable.ic_cat_beauty),
    CategoryCard(R.drawable.onb_cat_general, R.string.onboarding_cat_general, R.drawable.ic_cat_general),
)
// -----------------------------------------------------------------------------------------------------

/** The size the mock-up was made at, in dp. Every position below is measured in this frame. */
private const val FRAME_WIDTH = 440
private const val FRAME_HEIGHT = 978

private val CardEdge = Color(0xFFE4F3EB)
private val IconBack = Color(0xFFE2F4EB)

@Composable
internal fun CategoriesPage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("onboarding_categories")) {
        val scale = minOf(maxWidth / FRAME_WIDTH.dp, maxHeight / FRAME_HEIGHT.dp)
        // The same wave background as the second page, filling the whole screen behind the scaled frame.
        Image(
            painter = painterResource(R.drawable.onb_intro_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val outer = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(outer.density * scale, outer.fontScale)) {
            Box(modifier = Modifier.align(Alignment.TopCenter).requiredSize(FRAME_WIDTH.dp, FRAME_HEIGHT.dp)) {
                DecorationArt()
                Header()
                CardGrid()
            }
        }
    }
}

/** A picture placed by its top-left corner and size in the frame; nothing is drawn while [picture] is still null. */
@Composable
private fun Art(picture: Int?, x: Int, y: Int, width: Int, height: Int) {
    if (picture == null) return
    Image(
        painter = painterResource(picture),
        contentDescription = null,
        modifier = Modifier.offset(x.dp, y.dp).size(width.dp, height.dp),
    )
}

@Composable
private fun DecorationArt() {
    // ===================================== PASTE ZONE: start =====================================
    Art(CornerBag, x = 0, y = 26, width = 178, height = 160)
    Art(CornerTill, x = 326, y = 107, width = 114, height = 215)
    Art(LeafTopRight, x = 338, y = 12, width = 30, height = 30)
    Art(LeafMiddle, x = 157, y = 133, width = 34, height = 34)
    Art(LeafBlurLeft, x = 37, y = 243, width = 50, height = 40)
    Art(LeafRight, x = 374, y = 275, width = 44, height = 44)
    Art(LeavesBottomLeft, x = 0, y = 878, width = 150, height = 100)
    Art(LeafBottomMiddle, x = 112, y = 876, width = 40, height = 40)
    Art(LeafBottomRight, x = 262, y = 936, width = 70, height = 42)
    // ====================================== PASTE ZONE: end ======================================
}

@Composable
private fun Header() {
    // "Choose what you do": the part named by onboarding_cats_title_accent is green.
    val title = stringResource(R.string.onboarding_cats_title)
    val accent = stringResource(R.string.onboarding_cats_title_accent)
    val styled = buildAnnotatedString {
        append(title)
        val start = title.indexOf(accent)
        if (accent.isNotEmpty() && start >= 0) addStyle(SpanStyle(color = AuthEmerald), start, start + accent.length)
    }
    Box(Modifier.fillMaxWidth().offset(y = 184.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            styled,
            style = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = AuthInk, textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }

    Box(Modifier.fillMaxWidth().offset(y = 286.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            stringResource(R.string.onboarding_cats_body),
            style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = AuthInkMuted, textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 240.dp),
        )
    }
}

@Composable
private fun CardGrid() {
    val columns = listOf(25, 224)
    val rows = listOf(338 to 170, 518 to 170, 697 to 162)
    CATEGORY_CARDS.forEachIndexed { index, card ->
        val (top, height) = rows[index / 2]
        CategoryTile(
            card,
            Modifier.offset(columns[index % 2].dp, top.dp).size(191.dp, height.dp).testTag("onboarding_category_$index"),
        )
    }
}

@Composable
private fun CategoryTile(card: CategoryCard, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = AuthCard,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, CardEdge),
    ) {
        Column {
            Illustration(card.picture, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                card.icon?.let { AuxoArtwork(it, contentDescription = null, size = 42.dp) }
                    ?: Box(Modifier.size(42.dp).background(IconBack, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(card.label),
                    style = TextStyle(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = AuthInk),
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(22.dp).background(IconBack, CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(8.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.3f, size.height * 0.1f)
                            lineTo(size.width * 0.8f, size.height * 0.5f)
                            lineTo(size.width * 0.3f, size.height * 0.9f)
                        }
                        drawPath(path, AuthEmerald, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}
