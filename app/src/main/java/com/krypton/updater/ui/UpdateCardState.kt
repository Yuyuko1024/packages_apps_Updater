/*
 * Copyright (C) 2022 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krypton.updater.ui

import android.content.Context
import android.content.Intent
import android.content.res.Resources

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

import com.krypton.updater.R
import com.krypton.updater.services.UpdateInstallerService
import com.krypton.updater.viewmodel.UpdateViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class UpdateCardState(
    coroutineScope: CoroutineScope,
    private val viewModel: UpdateViewModel,
    private val service: UpdateInstallerService?,
    private val snackbarHostState: SnackbarHostState,
    private val context: Context,
    private val resources: Resources
) {

    val titleText: String
        get() = resources.getString(R.string.install_update)

    val shouldShowProgress: Flow<Boolean>
        get() = viewModel.updateState.map { !it.idle && !it.finished }

    val progressDescriptionText: Flow<String>
        get() = viewModel.updateState.combine(progress) { state, progress ->
            when {
                state.initializing -> resources.getString(R.string.initializing)
                state.updating -> resources.getString(
                    R.string.installing_update_format,
                    String.format("%.2f", progress)
                )
                state.paused -> resources.getString(R.string.installation_paused)
                state.failed -> resources.getString(R.string.installation_failed)
                else -> resources.getString(R.string.installing_update)
            }
        }

    val progress: Flow<Float>
        get() = viewModel.updateProgress

    val shouldShowLeadingActionButton: Flow<Boolean>
        get() = viewModel.updateState.map { it.updating || it.paused }

    val leadingActionButtonText: Flow<String?>
        get() = viewModel.updateState.map {
            when {
                it.updating -> resources.getString(R.string.pause)
                it.paused -> resources.getString(R.string.resume)
                else -> null
            }
        }

    val trailingActionButtonText: Flow<String>
        get() = viewModel.updateState.map {
            when {
                it.updating || it.paused -> resources.getString(android.R.string.cancel)
                it.finished -> resources.getString(R.string.reboot)
                else -> resources.getString(R.string.update)
            }
        }

    init {
        coroutineScope.launch {
            for (reason in viewModel.updateFailedReason) {
                snackbarHostState.showSnackbar(
                    reason ?: resources.getString(R.string.installation_failed),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun leadingAction() {
        viewModel.updateState.value.let {
            if (it.updating || it.paused) {
                service?.pauseOrResumeUpdate()
            }
        }
    }

    fun trailingAction() {
        viewModel.updateState.value.let {
            when {
                it.updating || it.paused -> service?.cancelUpdate()
                it.idle || it.initializing || it.failed -> startUpdate()
                it.finished -> service?.reboot()
            }
            return@let
        }
    }

    private fun startUpdate() {
        context.startService(
            Intent(context, UpdateInstallerService::class.java).apply {
                action = UpdateInstallerService.ACTION_START_UPDATE
            }
        )
    }
}

@Composable
fun rememberUpdateCardState(
    updateViewModel: UpdateViewModel,
    updateInstallerService: UpdateInstallerService?,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    context: Context = LocalContext.current,
    resources: Resources = context.resources
) = remember(
    updateViewModel,
    updateInstallerService,
    snackbarHostState,
    coroutineScope,
    context,
    resources
) {
    UpdateCardState(
        coroutineScope,
        updateViewModel,
        updateInstallerService,
        snackbarHostState,
        context,
        resources
    )
}