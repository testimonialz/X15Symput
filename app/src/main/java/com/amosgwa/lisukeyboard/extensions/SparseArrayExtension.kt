package com.amosgwa.lisukeyboard.extensions

import android.util.SparseArray

inline fun <T> SparseArray<T>.forEach(action: (key: Int, value: T) -> Unit) {
    for (index in 0 until size()) {
        action(keyAt(index), valueAt(index))
    }
}


