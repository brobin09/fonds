package com.brianrobinson.fonds.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.brianrobinson.fonds.data.DocStatus
import com.brianrobinson.fonds.data.DocumentEntity
import com.brianrobinson.fonds.data.DocumentRow
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.data.titleIsDraft
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import java.io.File

/** How many tiles fit across: three on a Pixel in portrait, more in landscape. */
@Composable
fun galleryColumns(): Int = ((LocalConfiguration.current.screenWidthDp - 32) / 112).coerceIn(2, 6)

/** Top-bar switch between list and gallery. The choice is remembered everywhere. */
@Composable
fun ViewToggle(gallery: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (gallery) Icons.Outlined.ViewList else Icons.Outlined.GridView,
            if (gallery) "Show as list" else "Show as gallery",
        )
    }
}

/**
 * Lays tiles out in rows inside a LazyColumn, so a gallery can sit under headers and forms
 * on the same scrolling screen.
 */
fun LazyListScope.tileGrid(
    rows: List<DocumentRow>,
    columns: Int,
    keyPrefix: String,
    tile: @Composable (DocumentRow, Modifier) -> Unit,
) {
    val chunks = rows.chunked(columns)
    items(chunks.size, key = { i -> "$keyPrefix-${chunks[i].first().document.id}" }) { i ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            chunks[i].forEach { r -> tile(r, Modifier.weight(1f)) }
            repeat(columns - chunks[i].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemTile(
    row: DocumentRow,
    modifier: Modifier = Modifier,
    caption: String? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val d = row.document
    val shape = RoundedCornerShape(8.dp)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.77f)
                .clip(shape)
                .background(Fonds.colors.labelPaper)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            if (row.thumbPath != null) {
                AsyncImage(
                    model = File(row.thumbPath),
                    contentDescription = d.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Outlined.Description, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.outline)
            }
            StatusDot(d, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (row.pageCount > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Text("${row.pageCount} pp", style = LabelType.small, color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            if (selecting) {
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                ) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Text(
            d.displayTitle(),
            style = MaterialTheme.typography.labelMedium,
            fontStyle = if (d.titleIsDraft()) FontStyle.Italic else FontStyle.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small badge on the tile corner: processing, error, draft waiting, reviewed, or to review. */
@Composable
private fun StatusDot(d: DocumentEntity, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = Color.White.copy(alpha = 0.92f), shape = CircleShape, modifier = modifier.size(22.dp)) {
        Box(contentAlignment = Alignment.Center) {
            when {
                d.status in DocStatus.BUSY -> CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = scheme.primary)
                d.status == DocStatus.ERROR -> Icon(Icons.Outlined.ErrorOutline, "Needs attention", Modifier.size(15.dp), tint = scheme.error)
                d.reviewed -> Icon(Icons.Outlined.Check, "Reviewed", Modifier.size(15.dp), tint = Fonds.colors.approved)
                d.status == DocStatus.DRAFTED -> Icon(Icons.Outlined.AutoAwesome, "Draft ready", Modifier.size(14.dp), tint = Color(0xFF8A6A2F))
                else -> Box(Modifier.size(8.dp).clip(CircleShape).background(scheme.outline))
            }
        }
    }
}
