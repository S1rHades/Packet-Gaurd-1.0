package io.github.romanvht.byedpi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.R
import kotlin.math.abs

@Composable
private fun posText(pos: CmdPos?, raw: String): String {
    if (pos == null) return raw
    val field = when (pos.field) {
        CmdPos.Field.SiteName -> stringResource(R.string.pg_field_sni)
        CmdPos.Field.HostHeader -> stringResource(R.string.pg_field_host)
        CmdPos.Field.Packet -> ""
    }
    val n = abs(pos.offset)
    val base = when {
        pos.field == CmdPos.Field.Packet && pos.anchor == CmdPos.Anchor.End -> stringResource(R.string.pg_pos_from_end, n)
        pos.field == CmdPos.Field.Packet -> stringResource(R.string.pg_pos_at, n)
        pos.anchor == CmdPos.Anchor.Middle -> stringResource(R.string.pg_pos_middle, field)
        pos.anchor == CmdPos.Anchor.End && pos.offset < 0 -> stringResource(R.string.pg_pos_end_before, n, field)
        pos.anchor == CmdPos.Anchor.End -> stringResource(R.string.pg_pos_end_after, n, field)
        pos.offset < 0 -> stringResource(R.string.pg_pos_before, n, field)
        else -> stringResource(R.string.pg_pos_in, n, field)
    }
    return if (pos.repeated) stringResource(R.string.pg_pos_repeated, base) else base
}

@Composable
private fun protoText(value: String): String =
    value.split(',').mapNotNull {
        when (it.trim()) {
            "t" -> "TLS"
            "h" -> "HTTP"
            "u" -> "UDP"
            "i" -> "IPv4"
            else -> null
        }
    }.joinToString(", ").ifEmpty { value }

/** One plain sentence for what an option does. */
@Composable
fun explainText(part: CmdPart): String {
    val where = posText(part.pos, part.value)
    return when (part.technique) {
        Technique.Split -> stringResource(R.string.pg_ex_split, where)
        Technique.Disorder -> stringResource(R.string.pg_ex_disorder, where)
        Technique.Oob -> stringResource(R.string.pg_ex_oob, where)
        Technique.Disoob -> stringResource(R.string.pg_ex_disoob, where)
        Technique.Fake -> stringResource(R.string.pg_ex_fake, where)
        Technique.TlsRec -> stringResource(R.string.pg_ex_tlsrec, where)
        Technique.FakeTtl -> stringResource(R.string.pg_ex_ttl, part.value)
        Technique.FakeSni -> stringResource(R.string.pg_ex_fake_sni, part.value)
        Technique.FakeTlsMod -> stringResource(R.string.pg_ex_fake_tls_mod)
        Technique.FakeOffset -> stringResource(R.string.pg_ex_fake_offset, part.value)
        Technique.UdpFake -> stringResource(R.string.pg_ex_udp_fake, part.value)
        Technique.ModHttp -> stringResource(R.string.pg_ex_mod_http)
        Technique.Auto -> stringResource(R.string.pg_ex_auto)
        Technique.Timeout -> stringResource(R.string.pg_ex_timeout, part.value)
        Technique.Proto -> stringResource(R.string.pg_ex_proto, protoText(part.value))
        Technique.Hosts -> stringResource(R.string.pg_ex_hosts)
        Technique.Md5Sig -> stringResource(R.string.pg_ex_md5)
    }
}

/**
 * What a command line does, one option per line: the option in monospace, then a plain sentence.
 * Shows nothing for a command it can't explain. [max] limits the lines; the rest are counted.
 */
@Composable
fun CommandExplainer(command: String, modifier: Modifier = Modifier, max: Int = 6, title: Boolean = true) {
    val parts = remember(command) { explainCommand(command) }
    if (parts.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (title) Text(stringResource(R.string.pg_ex_title), color = Palette.TextSecondary, fontSize = 12.sp)
        parts.take(max).forEach { p ->
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    p.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Sky,
                    modifier = Modifier.widthIn(min = 56.dp, max = 120.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(explainText(p), fontSize = 13.sp, lineHeight = 18.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            }
        }
        if (parts.size > max) {
            Text(
                stringResource(R.string.pg_ex_more, parts.size - max), color = Palette.TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
