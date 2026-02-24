package com.sabrina.uxiaproject.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.sabrina.uxiaproject.R

class AuthBlockerDialog : DialogFragment() {

    interface AuthBlockerListener {
        fun onGoToRegister()
        fun onGoToLogin()  // Nou mètode
    }

    private var listener: AuthBlockerListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? AuthBlockerListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_auth_blocker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnGoToRegister).setOnClickListener {
            listener?.onGoToRegister()
            dismiss()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}