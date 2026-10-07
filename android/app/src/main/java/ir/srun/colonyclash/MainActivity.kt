package ir.srun.colonyclash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.srun.colonyclash.matchmaking.DeepLinkRoute
import ir.srun.colonyclash.ui.ColonyGameApp

class MainActivity : ComponentActivity() {
    private var onContactSelected: ((String, String) -> Unit)? = null
    private var deepLinkRoute by mutableStateOf<DeepLinkRoute?>(null)

    private val contactPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    onContactSelected?.invoke(
                        cursor.getString(0).orEmpty(),
                        cursor.getString(1).orEmpty()
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkRoute = DeepLinkRoute.parse(intent?.data)
        setContent {
            ColonyGameApp(
                deepLinkRoute = deepLinkRoute,
                onDeepLinkConsumed = { deepLinkRoute = null },
                onPickContact = { callback ->
                    onContactSelected = callback
                    pickContact()
                },
                onShare = ::shareText,
                onSendSms = ::sendSms
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRoute = DeepLinkRoute.parse(intent.data)
    }

    private fun pickContact() {
        contactPicker.launch(
            Intent(
                Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            )
        )
    }

    private fun shareText(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.share)
            )
        )
    }

    private fun sendSms(phone: String, text: String) {
        val target = Uri.parse("smsto:${Uri.encode(phone)}")
        val intent = Intent(Intent.ACTION_SENDTO, target).apply {
            putExtra("sms_body", text)
        }
        runCatching { startActivity(intent) }
            .onFailure { shareText(text) }
    }
}
