package com.amosgwa.lisukeyboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import android.util.Log
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.util.containsKey
import com.amosgwa.lisukeyboard.common.KeyStyle
import com.amosgwa.lisukeyboard.common.KeyboardStyle
import com.amosgwa.lisukeyboard.common.PageType.Companion.NORMAL
import com.amosgwa.lisukeyboard.common.PageType.Companion.SHIFT
import com.amosgwa.lisukeyboard.common.PageType.Companion.SYMBOL
import com.amosgwa.lisukeyboard.common.Styles
import com.amosgwa.lisukeyboard.data.KeyboardPreferences
import com.amosgwa.lisukeyboard.data.KeyboardPreferences.Companion.KEY_ENABLE_SOUND
import com.amosgwa.lisukeyboard.data.KeyboardPreferences.Companion.KEY_ENABLE_VIBRATION
import com.amosgwa.lisukeyboard.data.KeyboardPreferences.Companion.KEY_NEEDS_RELOAD
import com.amosgwa.lisukeyboard.keyboardinflater.CustomKeyboard
import com.amosgwa.lisukeyboard.view.inputmethodview.CustomInputMethodView
import com.amosgwa.lisukeyboard.view.inputmethodview.KeyboardActionListener
import kotlin.properties.Delegates


class CustomInputMethodService : InputMethodService(), KeyboardActionListener {

    private lateinit var customInputMethodView: CustomInputMethodView

    private lateinit var keyboardNormal: CustomKeyboard
    private lateinit var keyboardShift: CustomKeyboard
    private lateinit var keyboardSymbol: CustomKeyboard

    private var languageNames: MutableList<String> = mutableListOf()
    private var languageXmlRes: MutableList<Int> = mutableListOf()
    private var languageShiftXmlRes: MutableList<Int> = mutableListOf()
    private var languageSymbolXmlRes: MutableList<Int> = mutableListOf()

    private var keyboardsOfLanguages = SparseArray<SparseArray<CustomKeyboard>>()

    private var currentSelectedLanguageIdx = 0
    private var enableVibration = true
    private var enableSound = true

    private var currentKeyboardPage by Delegates.observable<Int?>(null) { _, _, newPage ->
        newPage?.let {
            customInputMethodView.updateKeyboardPage(newPage)
        }
    }

    private lateinit var preferences: KeyboardPreferences

    override fun onCreate() {
        super.onCreate()
        initSharedPreference()
        loadKeyCodes()
        initKeyboards()
    }

    private fun initSharedPreference() {
        preferences = KeyboardPreferences(applicationContext)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (preferences.getBoolean(KEY_NEEDS_RELOAD)) {
            loadSharedPreferences()
        }
    }

    override fun onInitializeInterface() {
        initKeyboards()
        super.onInitializeInterface()
    }

    private fun initKeyboards() {
        resetLoadedData()
        loadLanguages()
        loadStyles()
        loadSharedPreferences()
    }

    private fun resetLoadedData() {
        languageNames.clear()
        languageXmlRes.clear()
        languageShiftXmlRes.clear()
        languageSymbolXmlRes.clear()
        keyboardsOfLanguages.clear()
        currentKeyboardPage = null
    }

    private fun renderCurrentLanguage() {
        if (keyboardsOfLanguages.containsKey(currentSelectedLanguageIdx)) {
            customInputMethodView.updateKeyboardLanguage(currentSelectedLanguageIdx)
        }
    }

    private fun loadSharedPreferences() {
        currentSelectedLanguageIdx = preferences.getInt(KeyboardPreferences.KEY_CURRENT_LANGUAGE_IDX, 0)
        enableVibration = preferences.getBoolean(KEY_ENABLE_VIBRATION)
        enableSound = preferences.getBoolean(KEY_ENABLE_SOUND)
    }

    private fun loadStyles() {
        // Load the styles and store them as Singleton values
        Styles.keyboardStyle = KeyboardStyle(getColorInt(R.color.default_keyboard_background_color))

        Styles.keyStyle = KeyStyle(
                getColorInt(R.color.default_key_normal_background_color),
                getColorInt(R.color.default_key_pressed_background_color),
                getColorInt(R.color.default_key_shadow_color),
                getColorInt(R.color.default_key_label_color),
                getColorInt(R.color.default_key_sub_label_color)
        )
    }

    @ColorInt
    private fun getColorInt(@ColorRes res: Int): Int {
        return resources.getColor(res, null)
    }

    override fun onCreateInputView(): View {
        customInputMethodView = layoutInflater.inflate(R.layout.keyboard, null) as CustomInputMethodView
        val keyboard = keyboardsOfLanguages[currentSelectedLanguageIdx]
        keyboard?.let {
            customInputMethodView.prepareAllKeyboardsForRendering(keyboardsOfLanguages, currentSelectedLanguageIdx)
            customInputMethodView.keyboardViewListener = this
            customInputMethodView.updateKeyboardLanguage(currentSelectedLanguageIdx)
        }
        return customInputMethodView
    }

    private fun loadLanguages() {
        val languagesArray = resources.obtainTypedArray(R.array.languages)
        val keyboards: SparseArray<CustomKeyboard> = SparseArray()
        var eachLanguageTypedArray: TypedArray? = null
        for (i in 0 until languagesArray.length()) {
            val id = languagesArray.getResourceId(i, -1)
            if (id == -1) {
                throw IllegalStateException("Invalid language array resource")
            }
            eachLanguageTypedArray = resources.obtainTypedArray(id)
            eachLanguageTypedArray?.let {
                val nameIdx = 0

                val languageName = it.getString(nameIdx)
                val xmlRes = it.getResourceId(RES_IDX, -1)
                val shiftXmlRes = it.getResourceId(SHIFT_IDX, -1)
                val symbolXmlRes = it.getResourceId(SYM_IDX, -1)

                if (languageName == null || xmlRes == -1 || shiftXmlRes == -1 || symbolXmlRes == -1) {
                    throw IllegalStateException("Make sure the arrays resources contain name, xml, and shift xml")
                }

                languageNames.add(languageName)
                languageXmlRes.add(xmlRes)
                languageShiftXmlRes.add(shiftXmlRes)
                languageSymbolXmlRes.add(symbolXmlRes)
            }

            keyboardNormal = CustomKeyboard(this, languageXmlRes.last(), NORMAL, languageNames.last())
            keyboardShift = CustomKeyboard(this, languageShiftXmlRes.last(), SHIFT, languageNames.last())
            keyboardSymbol = CustomKeyboard(this, languageSymbolXmlRes.last(), SYMBOL, languageNames.last())

            keyboards.clear()
            keyboards.append(NORMAL, keyboardNormal)
            keyboards.append(SHIFT, keyboardShift)
            keyboards.append(SYMBOL, keyboardSymbol)
            keyboardsOfLanguages.put(i, keyboards.clone())
        }

        eachLanguageTypedArray?.recycle()
        languagesArray.recycle()
    }

    override fun onSwipeRight() {
        if (BuildConfig.DEBUG) {
            Log.d("///AMOS", "SWIPE RIGHT")
        }
    }

    override fun onSwipeLeft() {
        if (BuildConfig.DEBUG) {
            Log.d("///AMOS", "SWIPE LEFT")
        }
    }

    override fun onSwipeUp() {
        if (BuildConfig.DEBUG) {
            Log.d("///AMOS", "SWIPE UP")
        }
    }

    override fun onSwipeDown() {
        if (BuildConfig.DEBUG) {
            Log.d("///AMOS", "SWIPE DOWN")
        }
    }

    override fun onChangeKeyboardSwipe(direction: Int) {
        changeLanguage(direction)
    }

    private fun saveCurrentState() {
        preferences.putInt(KeyboardPreferences.KEY_CURRENT_LANGUAGE_IDX, currentSelectedLanguageIdx)
    }

    private fun changeLanguage(direction: Int) {
        currentSelectedLanguageIdx = ((currentSelectedLanguageIdx + direction) + languageNames.size) % languageNames.size
        if (BuildConfig.DEBUG) {
            Log.d("///AMOS", "CHANGE DIRECTION $currentSelectedLanguageIdx")
        }
        saveCurrentState()
        renderCurrentLanguage()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection
        if (enableVibration) vibrate()
        if (enableSound) playClick(primaryCode)
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                val selectedText: CharSequence? = inputConnection.getSelectedText(0)
                if (selectedText == null) {
                    inputConnection.deleteSurroundingText(1, 0)
                } else {
                    if (selectedText.isEmpty()) {
                        inputConnection.deleteSurroundingText(1, 0)
                    } else {
                        inputConnection.commitText("", 1)
                    }
                }
            }
            KEYCODE_ABC -> {
                currentKeyboardPage = NORMAL
                return
            }
            Keyboard.KEYCODE_SHIFT -> {
                currentKeyboardPage = SHIFT
                return
            }
            KEYCODE_UNSHIFT -> {
                currentKeyboardPage = NORMAL
                return
            }
            KEYCODE_123 -> {
                currentKeyboardPage = SYMBOL
                return
            }
            KEYCODE_MYA_TI_MYA_NA -> {
                val output = KEYCODE_MYA_TI.toChar().toString() + KEYCODE_MYA_NA.toChar()
                inputConnection.commitText(output, 2)
            }
            KEYCODE_NA_PO_MYA_NA -> {
                val output = KEYCODE_NA_PO.toChar().toString() + KEYCODE_MYA_NA.toChar()
                inputConnection.commitText(output, 2)
            }
            KEYCODE_LANGUAGE -> {
                val mgr = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                mgr?.showInputMethodPicker()
            }
            Keyboard.KEYCODE_DONE -> {
                val event = (KeyEvent(0, 0, MotionEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER, 0, 0, 0, 0,
                        KeyEvent.FLAG_SOFT_KEYBOARD))
                inputConnection.sendKeyEvent(event)
            }
            else -> {
                inputConnection.commitText(primaryCode.toChar().toString(), 1)
            }
        }
        // Switch back to normal if the selected page type is shift.
        if (currentKeyboardPage == SHIFT) {
            currentKeyboardPage = NORMAL
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, 150))
        } else {
            vibrator.vibrate(20)
        }
    }

    private fun playClick(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (keyCode) {
            32 -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR)
            Keyboard.KEYCODE_DONE, 10 -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN)
            Keyboard.KEYCODE_DELETE -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE)
            else -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    private fun loadKeyCodes() {
        KEYCODE_UNSHIFT = resources.getInteger(R.integer.keycode_unshift)
        KEYCODE_ABC = resources.getInteger(R.integer.keycode_abc)
        KEYCODE_123 = resources.getInteger(R.integer.keycode_sym)
        KEYCODE_SPACE = resources.getInteger(R.integer.keycode_space)
        KEYCODE_LANGUAGE = resources.getInteger(R.integer.keycode_switch_next_keyboard)
        KEYCODE_NA_PO_MYA_NA = resources.getInteger(R.integer.keycode_na_po_mya_na)
        KEYCODE_MYA_TI_MYA_NA = resources.getInteger(R.integer.keycode_mya_ti_mya_na)
        KEYCODE_MYA_TI = resources.getInteger(R.integer.keycode_mya_ti)
        KEYCODE_MYA_NA = resources.getInteger(R.integer.keycode_mya_na)
        KEYCODE_NA_PO = resources.getInteger(R.integer.keycode_na_po)
    }

    companion object {
        var KEYCODE_NONE = -777
        var KEYCODE_UNSHIFT = KEYCODE_NONE
        var KEYCODE_ABC = KEYCODE_NONE
        var KEYCODE_123 = KEYCODE_NONE
        var KEYCODE_SPACE = KEYCODE_NONE
        var KEYCODE_NA_PO_MYA_NA = KEYCODE_NONE
        var KEYCODE_MYA_TI_MYA_NA = KEYCODE_NONE
        var KEYCODE_LANGUAGE = KEYCODE_NONE
        var KEYCODE_NA_PO = KEYCODE_NONE
        var KEYCODE_MYA_NA = KEYCODE_NONE
        var KEYCODE_MYA_TI = KEYCODE_NONE

        const val RES_IDX = 1
        const val SHIFT_IDX = 2
        const val SYM_IDX = 3
    }
}
