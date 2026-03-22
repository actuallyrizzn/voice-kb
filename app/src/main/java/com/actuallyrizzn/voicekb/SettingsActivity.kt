/*
 * Voice KB — Android dictation with Venice AI cleanup
 * Copyright (C) 2026 actuallyrizzn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.actuallyrizzn.voicekb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.actuallyrizzn.voicekb.databinding.ActivitySettingsBinding
import android.provider.Settings
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SecureSettingsStore
    private var models: List<VeniceTextModel> = emptyList()

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* IME checks permission again when dictating */ }
    private val dictionaryPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            binding.previewSystemGlossary.setText(R.string.system_dictionary_permission_required)
            return@registerForActivityResult
        }
        refreshTermPreviews()
    }

    private fun hasDictionaryPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            READ_USER_DICTIONARY_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        settings = SecureSettingsStore(this)
        if (!settings.isEncryptedStore) {
            Toast.makeText(
                this,
                R.string.settings_storage_fallback_warning,
                Toast.LENGTH_LONG,
            ).show()
        }
        binding.switchSanitize.isChecked = settings.sanitizeEnabled()
        binding.editVeniceKey.setText(settings.veniceApiKey())
        binding.editGlossary.setText("")
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { GlossaryLoader.loadUserTerms(this@SettingsActivity) }
            binding.editGlossary.setText(existing.joinToString("\n"))
        }
        refreshTermPreviews()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (!hasDictionaryPermission()) {
            dictionaryPermission.launch(READ_USER_DICTIONARY_PERMISSION)
        }

        binding.buttonOpenSystemDictionary.setOnClickListener {
            val intent = Intent(Settings.ACTION_USER_DICTIONARY_SETTINGS)
            runCatching { startActivity(intent) }
        }
        binding.buttonClearGlossary.setOnClickListener { confirmClearGlossary() }

        binding.buttonSave.setOnClickListener { save() }
        binding.buttonRefreshModels.setOnClickListener { loadModels() }
        loadModels()
    }

    private fun loadModels() {
        lifecycleScope.launch {
            binding.buttonRefreshModels.isEnabled = false
            val result = withContext(Dispatchers.IO) {
                try {
                    ModelLoadResult(models = VeniceApi.listTextModels(settings.veniceBaseUrl()), error = null)
                } catch (ex: VeniceApi.VeniceApiException.Network) {
                    ModelLoadResult(
                        models = emptyList(),
                        error = if (ex.message?.contains("connect", ignoreCase = true) == true) {
                            R.string.settings_model_refresh_network_error
                        } else {
                            R.string.settings_model_refresh_failed
                        },
                    )
                } catch (ex: VeniceApi.VeniceApiException.Parsing) {
                    ModelLoadResult(
                        models = emptyList(),
                        error = R.string.settings_model_refresh_parse_error,
                    )
                } catch (ex: VeniceApi.VeniceApiException.Http) {
                    ModelLoadResult(
                        models = emptyList(),
                        error = if (ex.code in 500..599) {
                            R.string.settings_model_refresh_server_error
                        } else {
                            R.string.settings_model_refresh_failed
                        },
                    )
                } catch (_: Exception) {
                    ModelLoadResult(models = emptyList(), error = R.string.settings_model_refresh_failed)
                }
            }
            val list = result.models
            if (result.error != null) {
                Toast.makeText(this@SettingsActivity, result.error, Toast.LENGTH_LONG).show()
            }
            models = list
            if (list.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.settings_no_text_models, Toast.LENGTH_LONG).show()
            }
            val labels = list.map { m ->
                val label = m.displayName?.takeIf { it.isNotEmpty() } ?: m.id
                val price = if (m.pricingScore < 1e9) {
                    " (pricing idx ${"%.3f".format(m.pricingScore)})"
                } else {
                    ""
                }
                "$label — ${m.id}$price"
            }
            val adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels,
            )
            binding.spinnerModel.adapter = adapter
            val saved = settings.veniceModelId()
            val idx = list.indexOfFirst { it.id == saved }.takeIf { it >= 0 } ?: 0
            if (list.isNotEmpty()) {
                binding.spinnerModel.setSelection(idx.coerceAtMost(list.lastIndex))
            }
            binding.buttonRefreshModels.isEnabled = true
        }
    }

    private data class ModelLoadResult(
        val models: List<VeniceTextModel>,
        val error: Int?,
    )

    private data class TermPreviewResult(
        val customTerms: List<String>,
        val systemTerms: List<String>,
    )

    private fun refreshTermPreviews() {
        lifecycleScope.launch {
            val terms = withContext(Dispatchers.IO) {
                TermPreviewResult(
                    customTerms = GlossaryLoader.loadUserTerms(this@SettingsActivity),
                    systemTerms = if (hasDictionaryPermission()) {
                        UserDictionaryLoader.loadWords(contentResolver)
                    } else {
                        emptyList()
                    },
                )
            }
            binding.previewCustomGlossary.text = terms.customTerms.joinToString("\n").ifEmpty {
                getString(R.string.term_list_empty)
            }
            binding.previewSystemGlossary.text = if (hasDictionaryPermission()) {
                terms.systemTerms.joinToString("\n").ifEmpty {
                    getString(R.string.system_dictionary_not_available)
                }
            } else {
                getString(R.string.system_dictionary_permission_required)
            }
        }
    }

    private fun save() {
        val keyView: TextInputEditText = binding.editVeniceKey
        val key = keyView.text?.toString()?.trim().orEmpty()
        val modelIdx = binding.spinnerModel.selectedItemPosition
        val modelId = models.getOrNull(modelIdx)?.id.orEmpty()
        val glossary = binding.editGlossary.text?.toString().orEmpty()
        if (binding.switchSanitize.isChecked && key.isNotEmpty() && modelId.isEmpty()) {
            Toast.makeText(
                this,
                "Load the model list and select a text model.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { GlossaryLoader.saveUserTerms(this@SettingsActivity, glossary) }
            settings.prefs.edit().apply {
                putString(Prefs.VENICE_API_KEY, key)
                putString(Prefs.VENICE_MODEL_ID, modelId)
                putBoolean(Prefs.SANITIZE_ENABLED, binding.switchSanitize.isChecked)
                apply()
            }
            refreshTermPreviews()
            Toast.makeText(this@SettingsActivity, R.string.save_glossary_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearGlossary() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_glossary_confirm_title)
            .setMessage(R.string.clear_glossary_confirm_message)
            .setPositiveButton(R.string.clear_glossary_confirm_positive) { _: DialogInterface, _: Int ->
                clearGlossary()
            }
            .setNegativeButton(R.string.clear_glossary_confirm_negative, null)
            .show()
    }

    private fun clearGlossary() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { GlossaryLoader.saveUserTerms(this@SettingsActivity, "") }
            binding.editGlossary.setText("")
            refreshTermPreviews()
            Toast.makeText(this@SettingsActivity, R.string.clear_glossary_done, Toast.LENGTH_SHORT).show()
        }
    }
}

private const val READ_USER_DICTIONARY_PERMISSION = "android.permission.READ_USER_DICTIONARY"
