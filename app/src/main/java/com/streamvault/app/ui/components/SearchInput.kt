package com.streamvault.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.streamvault.app.ui.theme.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * A TV-optimized search input field.
 * Handles focus delegation to the inner text field and shows the keyboard automatically.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    imeAction: ImeAction = ImeAction.Search,
    onSearch: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val borderColor = if (isFocused) FocusBorder else OnSurfaceDim.copy(alpha = 0.5f)
    val bgColor = if (isFocused) SurfaceHighlight else SurfaceElevated
    val borderWidth = if (isFocused) 2.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.hasFocus }
            .focusProperties {
                enter = { focusRequester }
            }
            .clickable { 
                focusRequester.requestFocus() 
                keyboardController?.show()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔍", modifier = Modifier.padding(end = 8.dp))
            
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && !isFocused) {
                    Text(
                        text = placeholder, 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = OnSurfaceDim
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = OnSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Primary),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() })
                )
            }
        }
    }
}
