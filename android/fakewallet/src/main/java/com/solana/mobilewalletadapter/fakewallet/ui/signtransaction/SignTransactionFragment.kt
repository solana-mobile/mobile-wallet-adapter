/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.signtransaction

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentSignTransactionBinding

class SignTransactionFragment : Fragment() {
    private lateinit var viewBinding: FragmentSignTransactionBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentSignTransactionBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }
}