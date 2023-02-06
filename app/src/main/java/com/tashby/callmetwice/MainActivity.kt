package com.tashby.callmetwice

import android.Manifest.permission.*
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tashby.callmetwice.data.WhitelistRepository
import com.tashby.callmetwice.ui.theme.CallMeTwiceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.lang.Math.ceil
import java.util.*

private const val WHITELIST_PREFERENCES = "whitelist_preferences"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = WHITELIST_PREFERENCES
)

class MainActivity : ComponentActivity() {
    lateinit var whitelistRepository: WhitelistRepository

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            // Handle the Intent
            val projection = arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME
            )

            if (intent != null && intent.data != null) {
                val uri = intent.data
                val cursor: Cursor? = uri?.let {
                    contentResolver.query(
                        it,
                        projection,
                        null,
                        null,
                        null
                    )
                }

                if (cursor != null) {
                    cursor.moveToFirst()

                    val nameColumnIndex: Int =
                        cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val name: String = cursor.getString(nameColumnIndex)

                    val dataProjection = arrayOf(
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.Contacts.Data.DATA1,
                        ContactsContract.Contacts.Data.MIMETYPE
                    )

                    val dataCursor = contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        dataProjection,
                        ContactsContract.Data.DISPLAY_NAME + " = ?",
                        arrayOf(name),
                        null);

                    if (dataCursor != null && dataCursor.moveToFirst()) {
                        // Get the indexes of the MIME type and data
                        val mimeIdx = dataCursor.getColumnIndex(
                            ContactsContract.Contacts.Data.MIMETYPE);
                        val dataIdx = dataCursor.getColumnIndex(
                            ContactsContract.Contacts.Data.DATA1);

                        var phone: String

                        // Match the data to the MIME type, store in variables
                        do {
                            val mime = dataCursor.getString(mimeIdx);
                            if (ContactsContract.CommonDataKinds.Phone
                                    .CONTENT_ITEM_TYPE.equals(mime, ignoreCase = true)) {
                                phone = dataCursor.getString(dataIdx);
                                runBlocking {
                                    whitelistRepository.addPhoneNumber(phone)
                                }
                            }
                        } while (dataCursor.moveToNext());

                        dataCursor.close()
                    }

                    cursor.close()
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        whitelistRepository = WhitelistRepository(dataStore)

        if (checkSelfPermission(READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(READ_CONTACTS, READ_CALL_LOG, READ_PHONE_STATE))
        }

        setContent {
            CallMeTwiceTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    CallMeTwiceApp(this, whitelistRepository)
                }
            }
        }
    }


    fun openContacts() {
        val intent = Intent(
            Intent.ACTION_PICK,
            ContactsContract.Contacts.CONTENT_URI
        )

        startForResult.launch(intent)
    }



    companion object {
        fun checkPhoneNumber(context: Context, number: String) {
            if (checkWhitelist(context, number)) {
                if (checkCallLog(context, number)) {
                    val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    val streamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                    audioManager.setStreamVolume(AudioManager.STREAM_RING,
                        (streamMaxVolume * 0.7).toInt(), AudioManager.FLAG_ALLOW_RINGER_MODES or AudioManager.FLAG_PLAY_SOUND)
                }
            }
        }

        private fun checkWhitelist(context: Context, number: String): Boolean {
            val whitelistRepository = WhitelistRepository(context.dataStore)

            val whiteList: String
            runBlocking(Dispatchers.IO) {
                whiteList = whitelistRepository.whitelist.first()
            }

            val numbers = whiteList.split(",").toList()

            numbers.forEach {
                if (PhoneNumberUtils.compare(number, it)) {
                    return true
                }
            }

            return false
        }

        private fun checkCallLog(context: Context, number: String): Boolean {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            )

            val fiveMinutesInMilli = 300000
            val fiveMinutesAgo = Calendar.getInstance().timeInMillis - fiveMinutesInMilli
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                CallLog.Calls.DATE + " > ? AND " + CallLog.Calls.NUMBER + " = ?",
                arrayOf(fiveMinutesAgo.toString(), number),
                CallLog.Calls.NUMBER + " DESC"
            )

            if (cursor != null) {
                if (cursor.count > 0) {
                    return true
                }

                cursor.close()
            }

            return false
        }
    }
}

@Composable
fun CallMeTwiceApp(context: Context, whitelistRepository: WhitelistRepository, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Call Me Twice") })
        }
    ) { innerPadding ->
        CallMeTwiceContent(context, whitelistRepository, modifier.padding(innerPadding))
    }
}

@Composable
fun CallMeTwiceContent(context: Context, whitelistRepository: WhitelistRepository, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Button(onClick = {
            if (context.checkSelfPermission(READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "We need permission to read your contacts, please restart the app", Toast.LENGTH_SHORT).show();
            } else {
                (context as MainActivity).openContacts()
            }
        }) {
            Text(text = "Whitelist a contact")
        }

        val whiteListString = whitelistRepository.whitelist.collectAsState(initial = "").value

        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = whiteListString.split(","),
                key = { number -> number}
            ) { number ->
                Card(
                    backgroundColor = MaterialTheme.colors.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row (
                        modifier = modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = number,
                            modifier = Modifier.padding(start = 5.dp)
                        )

                        IconButton(
                            onClick = { runBlocking { whitelistRepository.removePhoneNumber(number) } }
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        }
                    }
                }
            }
        }
    }
}