package com.actuallyrizzn.voicekb

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.actuallyrizzn.voicekb.databinding.ActivitySettingsBinding
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

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

    private fun save() {
        val keyView: TextInputEditText = binding.editVeniceKey
        val key = keyView.text?.toString()?.trim().orEmpty()
        val modelIdx = binding.spinnerModel.selectedItemPosition
        val modelId = models.getOrNull(modelIdx)?.id.orEmpty()
        if (binding.switchSanitize.isChecked && key.isNotEmpty() && modelId.isEmpty()) {
            Toast.makeText(
                this,
                "Load the model list and select a text model.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        settings.prefs.edit().apply {
            putString(Prefs.VENICE_API_KEY, key)
            putString(Prefs.VENICE_MODEL_ID, modelId)
            putBoolean(Prefs.SANITIZE_ENABLED, binding.switchSanitize.isChecked)
            apply()
        }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }
}
