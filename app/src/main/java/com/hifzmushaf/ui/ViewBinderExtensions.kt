package com.quranhabit.ui

import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

fun <T : ViewBinding> Fragment.clearBinding(
    binding: T?,
    cleanup: (T.() -> Unit) = {}
) {
    binding?.apply {
        cleanup()
    }
}
