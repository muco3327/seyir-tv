package tv.newtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

@Composable
fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TvPlayerControlButton(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        var boxModifier = Modifier
            .size(if (isPrimary) 58.dp else 46.dp)
            .scale(if (isFocused) 1.15f else 1.0f)
            .background(if (isFocused) Color.White else if (isPrimary) Color(0xFFE50914) else Color(0xFF22262E), CircleShape)
            .border(if (isFocused) 3.dp else 1.dp, if (isFocused) Color(0xFFFFCC00) else Color(0xFF333842), CircleShape)
        if (focusRequester != null) boxModifier = boxModifier.focusRequester(focusRequester)
        Box(
            modifier = boxModifier.clickable(
                interactionSource = interactionSource, indication = null, onClick = onClick
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (isPrimary) 28.dp else 22.dp),
                tint = if (isFocused) Color.Black else Color.White
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (isFocused) Color.White else Color(0xFFCCCCCC), fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal)
    }
}
