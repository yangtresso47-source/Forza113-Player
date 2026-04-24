package com.kuqforza.data.validation

import com.kuqforza.data.util.ProviderInputSanitizer
import com.kuqforza.data.util.UrlSecurityPolicy
import com.kuqforza.domain.manager.ProviderSetupInputValidator
import com.kuqforza.domain.manager.ValidatedM3uProviderInput
import com.kuqforza.domain.manager.ValidatedStalkerProviderInput
import com.kuqforza.domain.manager.ValidatedXtreamProviderInput
import com.kuqforza.domain.model.Result
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderSetupInputValidatorImpl @Inject constructor() : ProviderSetupInputValidator {

    override fun validateXtream(
        serverUrl: String,
        username: String,
        name: String
    ): Result<ValidatedXtreamProviderInput> {
        val normalizedServerUrl = ProviderInputSanitizer.normalizeUrl(serverUrl)
        val normalizedUsername = ProviderInputSanitizer.normalizeUsername(username)
        val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)

        if (normalizedServerUrl.isBlank()) {
            return Result.error("Please enter server URL")
        }
        ProviderInputSanitizer.validateUrl(normalizedServerUrl)?.let { message ->
            return Result.error(message)
        }
        UrlSecurityPolicy.validateXtreamServerUrl(normalizedServerUrl)?.let { message ->
            return Result.error(message)
        }
        if (normalizedUsername.isBlank()) {
            return Result.error("Please enter username")
        }

        return Result.success(
            ValidatedXtreamProviderInput(
                serverUrl = normalizedServerUrl,
                username = normalizedUsername,
                name = normalizedName
            )
        )
    }

    override fun validateM3u(
        url: String,
        name: String
    ): Result<ValidatedM3uProviderInput> {
        val normalizedUrl = ProviderInputSanitizer.normalizeUrl(url)
        val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)

        if (normalizedUrl.isBlank()) {
            return Result.error("Please enter M3U URL")
        }
        ProviderInputSanitizer.validateUrl(normalizedUrl)?.let { message ->
            return Result.error(message)
        }
        UrlSecurityPolicy.validatePlaylistSourceUrl(normalizedUrl)?.let { message ->
            return Result.error(message)
        }

        return Result.success(
            ValidatedM3uProviderInput(
                url = normalizedUrl,
                name = normalizedName
            )
        )
    }

    override fun validateStalker(
        portalUrl: String,
        macAddress: String,
        name: String,
        deviceProfile: String,
        timezone: String,
        locale: String
    ): Result<ValidatedStalkerProviderInput> {
        val normalizedPortalUrl = ProviderInputSanitizer.normalizeUrl(portalUrl)
        val normalizedMacAddress = ProviderInputSanitizer.normalizeMacAddress(macAddress)
        val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)
        val normalizedDeviceProfile = ProviderInputSanitizer.normalizeDeviceProfile(deviceProfile)
        val normalizedTimezone = ProviderInputSanitizer.normalizeTimezone(timezone)
        val normalizedLocale = ProviderInputSanitizer.normalizeLocale(locale)

        if (normalizedPortalUrl.isBlank()) {
            return Result.error("Please enter portal URL")
        }
        ProviderInputSanitizer.validateUrl(normalizedPortalUrl)?.let { message ->
            return Result.error(message)
        }
        UrlSecurityPolicy.validateStalkerPortalUrl(normalizedPortalUrl)?.let { message ->
            return Result.error(message)
        }
        ProviderInputSanitizer.validateMacAddress(normalizedMacAddress)?.let { message ->
            return Result.error(message)
        }

        return Result.success(
            ValidatedStalkerProviderInput(
                portalUrl = normalizedPortalUrl,
                macAddress = normalizedMacAddress,
                name = normalizedName,
                deviceProfile = normalizedDeviceProfile,
                timezone = normalizedTimezone,
                locale = normalizedLocale
            )
        )
    }
}
