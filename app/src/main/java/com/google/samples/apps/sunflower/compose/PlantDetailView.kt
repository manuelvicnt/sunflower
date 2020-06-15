/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.sunflower.compose

import android.widget.TextView
import androidx.animation.FloatPropKey
import androidx.animation.TransitionState
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.getValue
import androidx.compose.onCommit
import androidx.compose.remember
import androidx.compose.setValue
import androidx.compose.state
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LiveData
import androidx.ui.animation.Transition
import androidx.ui.core.Alignment
import androidx.ui.core.ContentScale
import androidx.ui.core.ContextAmbient
import androidx.ui.core.DensityAmbient
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Modifier
import androidx.ui.core.drawLayer
import androidx.ui.core.drawOpacity
import androidx.ui.core.globalPosition
import androidx.ui.core.onPositioned
import androidx.ui.foundation.Box
import androidx.ui.foundation.Icon
import androidx.ui.foundation.ScrollerPosition
import androidx.ui.foundation.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.foundation.drawBackground
import androidx.ui.foundation.shape.corner.CircleShape
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.layout.Arrangement
import androidx.ui.layout.Column
import androidx.ui.layout.ColumnScope.gravity
import androidx.ui.layout.Row
import androidx.ui.layout.Spacer
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.offset
import androidx.ui.layout.padding
import androidx.ui.layout.preferredHeight
import androidx.ui.layout.size
import androidx.ui.layout.sizeIn
import androidx.ui.layout.wrapContentSize
import androidx.ui.livedata.observeAsState
import androidx.ui.material.FloatingActionButton
import androidx.ui.material.IconButton
import androidx.ui.material.MaterialTheme
import androidx.ui.material.Surface
import androidx.ui.material.TopAppBar
import androidx.ui.material.icons.Icons
import androidx.ui.material.icons.filled.Add
import androidx.ui.material.icons.filled.ArrowBack
import androidx.ui.material.icons.filled.Share
import androidx.ui.res.stringResource
import androidx.ui.text.font.FontWeight
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.Dp
import androidx.ui.unit.IntSize
import androidx.ui.unit.dp
import androidx.ui.viewinterop.AndroidView
import com.google.samples.apps.sunflower.R
import com.google.samples.apps.sunflower.data.Plant
import dev.chrisbanes.accompanist.coil.CoilImageWithCrossfade
import dev.chrisbanes.accompanist.mdctheme.MaterialThemeFromMdcTheme

// TODO: Change this for WindowsInsets
private val StatusBarHeight = 24.dp
private const val ParallaxDelta = 2f
private const val HeaderTransitionOffset = 150f
private val SunflowerFabShape =
    RoundedCornerShape(topLeft = 0.dp, topRight = 30.dp, bottomRight = 0.dp, bottomLeft = 30.dp)

/**
 * As these callbacks are passed in through multiple Composables, to avoid having to name
 * parameters to not mix them up, they're aggregated in this class.
 */
data class PlantDetailsCallbacks(
    val onFabClicked: () -> Unit,
    val onBackClicked: () -> Unit,
    val onShareClicked: () -> Unit
)

@Composable
fun PlantDetails(
    plantObservable: LiveData<Plant>,
    isPlantedObservable: LiveData<Boolean>,
    callbacks: PlantDetailsCallbacks
) {
    val plant by plantObservable.observeAsState()
    val isPlanted by isPlantedObservable.observeAsState()

    if (plant != null && isPlanted != null) {
        PlantOverview(plant!!, isPlanted!!, callbacks)
    }
}

@Composable
private fun PlantOverview(
    plant: Plant,
    isPlanted: Boolean,
    callbacks: PlantDetailsCallbacks,
    modifier: Modifier = Modifier
) {
    val scrollerPosition = ScrollerPosition()
    var toolbarState by state { ToolbarState.HIDDEN }

    Transition(
        definition = toolbarTransitionDefinition,
        toState = toolbarState
    ) { transitionState ->
        Stack(modifier) {
            PlantDetailsContent(
                scrollerPosition = scrollerPosition,
                toolbarShown = toolbarState.toolbarShown,
                onToolbarShownUpdate = { newValue ->
                    toolbarState = toolbarStateFromBoolean(newValue)
                },
                plant = plant,
                isPlanted = isPlanted,
                callbacks = callbacks,
                transitionState = transitionState
            )
            PlantToolbar(toolbarState.toolbarShown, plant.name, callbacks, transitionState)
        }
    }
}

@Composable
private fun PlantDetailsContent(
    scrollerPosition: ScrollerPosition,
    toolbarShown: Boolean,
    onToolbarShownUpdate: (Boolean) -> Unit,
    plant: Plant,
    isPlanted: Boolean,
    callbacks: PlantDetailsCallbacks,
    transitionState: TransitionState
) {
    VerticalScroller(scrollerPosition) {
        var namePosition by state { Float.MAX_VALUE }
        onCommit(namePosition, scrollerPosition.value) {
            onToolbarShownUpdate(
                scrollerPosition.value > (namePosition + HeaderTransitionOffset)
            )
        }

        Hide(toolbarShown) { hideModifier ->
            PlantImageHeader(
                scrollerPosition, plant.imageUrl, callbacks.onFabClicked, isPlanted, hideModifier,
                Modifier.drawLayer(alpha = transitionState[contentAlphaKey], clip = false)
            )
        }
        PlantInformation(
            name = plant.name,
            wateringInterval = plant.wateringInterval,
            description = plant.description,
            onNamePositioned = {
                if (namePosition == Float.MAX_VALUE) {
                    namePosition = it.globalPosition.y
                }
            },
            toolbarShown = toolbarShown
        )
    }
}

@Composable
private fun PlantToolbar(
    toolbarShown: Boolean,
    plantName: String,
    callbacks: PlantDetailsCallbacks,
    transitionState: TransitionState
) {
    if (toolbarShown) {
        PlantDetailsToolbar(
            plantName = plantName,
            onBackClicked = callbacks.onBackClicked,
            onShareClicked = callbacks.onShareClicked,
            modifier = Modifier.drawOpacity(transitionState[toolbarAlphaKey])
        )
    } else {
        HeaderBarContent(
            onBackClicked = callbacks.onBackClicked,
            onShareClicked = callbacks.onShareClicked,
            modifier = Modifier.drawOpacity(transitionState[contentAlphaKey])
        )
    }
}

@Composable
private fun PlantDetailsToolbar(
    plantName: String,
    onBackClicked: () -> Unit,
    onShareClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Spacer( // TODO: This should react to WindowsInsets
            Modifier.preferredHeight(StatusBarHeight).fillMaxWidth()
                .drawBackground(MaterialTheme.colors.surface)
        )
        TopAppBar(
            backgroundColor = MaterialTheme.colors.surface
        ) {
            IconButton(onBackClicked, Modifier.gravity(Alignment.CenterVertically)) {
                Icon(Icons.Filled.ArrowBack)
            }
            Text(
                text = plantName,
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.weight(1f).fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            )
            IconButton(onShareClicked, Modifier.gravity(Alignment.CenterVertically)) {
                Icon(Icons.Filled.Share)
            }
        }
    }
}

@Composable
private fun PlantImageHeader(
    scrollerPosition: ScrollerPosition,
    imageUrl: String,
    onFabClicked: () -> Unit,
    isPlanted: Boolean,
    modifier: Modifier = Modifier,
    transitionModifier: Modifier = Modifier
) {
    val imageHeight = state { 0 }

    Stack(modifier.fillMaxWidth()) {
        PlantImage(scrollerPosition, imageUrl, transitionModifier.onPositioned {
            imageHeight.value = it.size.height
        })
        if (!isPlanted) {
            val fabModifier = if (imageHeight.value != 0) {
                Modifier.gravity(Alignment.TopEnd).padding(end = 8.dp)
                    .offset(y = getFabOffset(imageHeight.value, scrollerPosition))
                    .plus(transitionModifier)
            } else {
                Modifier
            }
            FloatingActionButton(
                onClick = onFabClicked,
                shape = SunflowerFabShape,
                modifier = fabModifier
            ) {
                Icon(Icons.Filled.Add)
            }
        }
    }
}

@Composable
private fun PlantImage(
    scrollerPosition: ScrollerPosition,
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    ParallaxEffect(scrollerPosition, modifier) { parallaxModifier ->
        CoilImageWithCrossfade(
            data = imageUrl,
            contentScale = ContentScale.Crop,
            modifier = parallaxModifier.fillMaxWidth().preferredHeight(278.dp)
        )
    }
}

@Composable
private fun HeaderBarContent(
    onBackClicked: () -> Unit,
    onShareClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxSize().padding(top = StatusBarHeight + 12.dp), Arrangement.SpaceBetween) {
        val iconModifier = Modifier.sizeIn(maxWidth = 32.dp, maxHeight = 32.dp)
            .drawBackground(color = Color.White, shape = CircleShape)

        IconButton(
            onClick = onBackClicked,
            modifier = Modifier.padding(start = 12.dp).plus(iconModifier)
        ) {
            Icon(Icons.Filled.ArrowBack)
        }
        IconButton(
            onClick = onShareClicked,
            modifier = Modifier.padding(end = 12.dp).plus(iconModifier)
        ) {
            Icon(Icons.Filled.Share)
        }
    }
}

@Composable
private fun PlantInformation(
    name: String,
    wateringInterval: Int,
    description: String,
    onNamePositioned: (LayoutCoordinates) -> Unit,
    toolbarShown: Boolean
) {
    Box(modifier = Modifier.padding(24.dp)) {
        Hide(toolbarShown) { hideModifier ->
            Text(
                text = name,
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                    .gravity(Alignment.CenterHorizontally).onPositioned {
                        onNamePositioned(it)
                    }.plus(hideModifier)
            )
        }
        Text(
            text = stringResource(id = R.string.watering_needs_prefix),
            color = MaterialTheme.colors.primaryVariant,
            style = MaterialTheme.typography.body1.copy(
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 8.dp).gravity(Alignment.CenterHorizontally)
        )
        Text(
            text = getQuantityString(R.plurals.watering_needs_suffix, wateringInterval),
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                .gravity(Alignment.CenterHorizontally)
        )
        PlantDescription(description)
    }
}

@Composable
private fun PlantDescription(description: String) {
    // Issue: User input doesn't work properly, HTML links cannot be clicked - b/158088138
    // HTML support coming to Compose - b/139320905
    AndroidView(resId = R.layout.item_plant_description) {
        (it as TextView).text = HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
}

@Composable
private fun ParallaxEffect(
    scrollerPosition: ScrollerPosition,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val offset = scrollerPosition.value / ParallaxDelta
    val offsetDp = with(DensityAmbient.current) { offset.toDp() }
    content(Modifier.padding(top = offsetDp).plus(modifier))
}

/**
 * Calculates offset FAB needs to keep aligned in the middle of the bottom of the picture.
 *
 * As the onPositioned in the image is invoked after scrollPosition has changed, there's a frame
 * delay.
 */
@Composable
private fun getFabOffset(imageHeight: Int, scrollerPosition: ScrollerPosition): Dp {
    return with(DensityAmbient.current) {
        imageHeight.toDp() + (scrollerPosition.value / ParallaxDelta).toDp() - (56 / 2).dp
    }
}

/**
 * Hides an element on the screen leaving its space occupied.
 *
 * This should be replaced with the visible modifier in the future: b/158837937
 */
@Composable
private fun Hide(hide: Boolean, content: @Composable (Modifier) -> Unit) {
    var contentSize by state { IntSize.Zero }
    if (hide) {
        val (width, height) = remember(contentSize) {
            with(DensityAmbient.current) {
                contentSize.width.toDp() to contentSize.height.toDp()
            }
        }
        Spacer(modifier = Modifier.size(width, height))
    } else {
        content(Modifier.onPositioned {
            contentSize = it.size
        })
    }
}

private enum class ToolbarState { HIDDEN, SHOWN }

private val ToolbarState.toolbarShown: Boolean
    get() = this == ToolbarState.SHOWN

private fun toolbarStateFromBoolean(show: Boolean): ToolbarState =
    if (show) ToolbarState.SHOWN
    else ToolbarState.HIDDEN

private val toolbarAlphaKey = FloatPropKey()
private val contentAlphaKey = FloatPropKey()

private val toolbarTransitionDefinition = transitionDefinition {
    state(ToolbarState.HIDDEN) {
        this[toolbarAlphaKey] = 0f
        this[contentAlphaKey] = 1f
    }
    state(ToolbarState.SHOWN) {
        this[toolbarAlphaKey] = 1f
        this[contentAlphaKey] = 0f
    }
    transition {
        toolbarAlphaKey using tween<Float> {
            duration = 250
        }
        contentAlphaKey using tween<Float> {
            duration = 250
        }
    }
}

@Preview
@Composable
private fun PlantOverviewPreview() {
    MaterialThemeFromMdcTheme(ContextAmbient.current) {
        Surface {
            PlantOverview(
                Plant("plantId", "Tomato", "HTML<br>description", 6),
                true,
                PlantDetailsCallbacks({ }, { }, { })
            )
        }
    }
}