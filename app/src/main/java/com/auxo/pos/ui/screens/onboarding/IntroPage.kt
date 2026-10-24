// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.auxo.pos.ui.components.rememberBackgroundPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auxo.pos.R

/*
 * THE SECOND ONBOARDING PAGE, kept in this one file so a new design export can be pasted straight in.
 *
 * To update it from a new export of the design (the Composable1() function):
 *   1. Replace everything between the two "PASTE ZONE" lines below with the Image(...) blocks of the new export,
 *      in the order the export gives them.
 *   2. In each pasted Image, replace   painter = ColorPainter(Color(0xFFCFD4DC))   with   painter = rememberBackgroundPainter(R.drawable.<name>)
 *      The names are in the list under "PICTURES" just below. The export only gives places and sizes, so which
 *      picture goes in which slot is the one thing that is decided by hand.
 *   3. If the export moved the text, change the numbers in IntroTexts() at the bottom.
 * Nothing else in this file needs to change: the frame below scales the pasted design to any screen.
 *
 * PICTURES (res/drawable-nodpi):
 *   onb_intro_bg       the wave background (fills the whole screen, outside the paste zone)
 *   onb_intro_hero     the shop picture: phone, scanner, bag, shirt, drinks, burger
 *   onb_leafpic_01, 03  two crisp leaves: 457 x 300 and 277 x 250
 *   onb_leafset_01..20  crisp leaves in the order they were uploaded, without 02 (129 x 428 up to 461 x 267)
 */

/** The size the design was made at, in dp. The pasted positions and sizes are all measured in this frame. */
private const val FRAME_WIDTH = 440
private const val FRAME_HEIGHT = 956

/**
 * Everything is laid out once, in dp, inside the design frame. The frame is then scaled as a whole (one factor
 * for both directions, so nothing is stretched or moves relative to anything else) to the largest size that fits
 * the screen, and pinned to the top. Every phone shows the same picture; only its overall size changes.
 */
@Composable
internal fun IntroPage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("onboarding_intro")) {
        val scale = minOf(maxWidth / FRAME_WIDTH.dp, maxHeight / FRAME_HEIGHT.dp)
        Image(
            painter = rememberBackgroundPainter(R.drawable.onb_intro_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val outer = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(outer.density * scale, outer.fontScale)) {
            Box(modifier = Modifier.align(Alignment.TopCenter).requiredSize(FRAME_WIDTH.dp, FRAME_HEIGHT.dp)) {
                IntroPictures()
                IntroTexts()
            }
        }
    }
}

@Composable
private fun IntroPictures() {
    // ===================================== PASTE ZONE: start =====================================
    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_17),
        contentDescription = null,
        modifier = Modifier
            .offset(379.dp, 855.dp)
            .size(58.dp, 34.dp)
            .alpha(0.7f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_10),
        contentDescription = null,
        modifier = Modifier
            .size(18.dp, 21.dp)
            .offset(105.dp, 870.dp)
            .alpha(0.7f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_20),
        contentDescription = null,
        modifier = Modifier
            .offset(147.dp, 66.dp)
            .size(42.dp, 54.dp)
            .alpha(0.8f)
            .blur(2.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_06),
        contentDescription = null,
        modifier = Modifier
            .size(51.dp, 60.dp)
            .offset(3.dp, 19.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_14),
        contentDescription = null,
        modifier = Modifier
            .size(33.dp, 73.dp)
            .offset(175.dp, 467.dp)
            .blur(1.dp)
            .alpha(0.5f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_19),
        contentDescription = null,
        modifier = Modifier
            .offset(74.dp, 370.dp)
            .size(46.dp, 36.dp)
            .alpha(0.6f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_03),
        contentDescription = null,
        modifier = Modifier
            .size(40.dp, 52.dp)
            .offset(-19.dp, 235.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_07),
        contentDescription = null,
        modifier = Modifier
            .offset(112.dp, 197.dp)
            .size(20.dp, 65.dp)
            .alpha(0.3f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_15),
        contentDescription = null,
        modifier = Modifier
            .offset(403.dp, 212.dp)
            .alpha(0.8f)
            .size(28.dp, 12.dp)
            .blur(1.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_intro_hero),
        contentDescription = null,
        modifier = Modifier
            .size(421.dp, 370.dp)
            .offset(9.dp, 447.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_04),
        contentDescription = null,
        modifier = Modifier
            .offset(-6.dp, 796.dp)
            .size(33.dp, 75.dp)
            .alpha(0.7f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_11),
        contentDescription = null,
        modifier = Modifier
            .size(38.dp, 34.dp)
            .offset(51.dp, 607.dp)
            .alpha(0.9f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_09),
        contentDescription = null,
        modifier = Modifier
            .offset(353.dp, 533.dp)
            .size(37.dp, 39.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_05),
        contentDescription = null,
        modifier = Modifier
            .size(43.dp, 28.dp)
            .offset(132.dp, 563.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_16),
        contentDescription = null,
        modifier = Modifier
            .offset(295.dp, 363.dp)
            .size(25.dp, 34.dp)
            .alpha(0.5f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_18),
        contentDescription = null,
        modifier = Modifier
            .offset(384.dp, 254.dp)
            .size(44.dp, 78.dp)
            .alpha(0.8f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_12),
        contentDescription = null,
        modifier = Modifier
            .offset(409.dp, 18.dp)
            .size(27.dp, 65.dp)
            .alpha(0.8f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_08),
        contentDescription = null,
        modifier = Modifier
            .offset(360.dp, 139.dp)
            .size(76.dp, 50.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_01),
        contentDescription = null,
        modifier = Modifier
            .offset(366.dp, 434.dp)
            .size(19.dp, 23.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafset_13),
        contentDescription = null,
        modifier = Modifier
            .offset(7.dp, 453.dp)
            .size(60.dp, 50.dp)
            .alpha(0.9f),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafpic_03),
        contentDescription = null,
        modifier = Modifier
            .size(54.dp, 48.dp)
            .offset(51.dp, 607.dp),
    )

    Image(
        painter = rememberBackgroundPainter(R.drawable.onb_leafpic_01),
        contentDescription = null,
        modifier = Modifier
            .size(60.dp, 41.dp)
            .offset(223.dp, 700.dp),
    )

    // ====================================== PASTE ZONE: end ======================================
}

/**
 * The headline and the line under it. They use the app's text resources (so Hindi and Bengali work), placed where
 * the design puts them: the headline starts 164 dp down, the line under it 259 dp down in a 268 dp wide box.
 */
@Composable
private fun IntroTexts() {
    // Narrow enough that the English headline breaks after "business", as in the design.
    Box(modifier = Modifier.fillMaxWidth().offset(y = 164.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            stringResource(R.string.onboarding_intro_title),
            style = TextStyle(
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
    Text(
        stringResource(R.string.onboarding_intro_body),
        style = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF433D3D),
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.offset(93.dp, 259.dp).width(268.dp),
    )
}
