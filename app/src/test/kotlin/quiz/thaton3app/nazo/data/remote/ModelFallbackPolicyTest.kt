package quiz.thaton3app.nazo.data.remote

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFallbackPolicyTest {

    @Test
    fun authenticationBillingQuotaAndRateLimitsNeverUseAnotherModel() {
        val statuses = listOf(401, 403, 402, 429)

        statuses.forEach { status ->
            val failure = ApiClient.httpFailure(status, ProviderKind.OPENAI)
            assertFalse("HTTP $status must not trigger an extra request", ModelFallbackPolicy.shouldTryAlternate(failure))
        }
        assertEquals(ApiFailureCategory.AUTHENTICATION, ApiClient.httpFailure(401, ProviderKind.GEMINI).category)
        assertEquals(ApiFailureCategory.BILLING, ApiClient.httpFailure(402, ProviderKind.GEMINI).category)
        assertEquals(
            ApiFailureCategory.RATE_LIMIT_OR_QUOTA,
            ApiClient.httpFailure(429, ProviderKind.GEMINI).category,
        )
    }

    @Test
    fun requestNetworkAndTemporaryProviderFailuresDoNotRetryAnotherModel() {
        listOf(400, 500, 502, 503).forEach { status ->
            assertFalse(
                ModelFallbackPolicy.shouldTryAlternate(
                    ApiClient.httpFailure(status, ProviderKind.OPENAI)
                )
            )
        }
        assertFalse(ModelFallbackPolicy.shouldTryAlternate(IOException("timeout")))
        assertFalse(ModelFallbackPolicy.shouldTryAlternate(IllegalArgumentException("provider")))
    }

    @Test
    fun onlyMissingModelAndInvalidModelResponseAreEligible() {
        val missing = ApiClient.httpFailure(404, ProviderKind.GEMINI)
        val malformed = ApiClient.invalidResponseFailure(
            IllegalStateException("Provider returned only 2 of 5 valid questions")
        )

        assertTrue(ModelFallbackPolicy.shouldTryAlternate(missing))
        assertTrue(ModelFallbackPolicy.shouldTryAlternate(malformed))
        assertEquals(ApiFailureCategory.MODEL_UNAVAILABLE, missing.category)
        assertEquals(ApiFailureCategory.INVALID_RESPONSE, malformed.category)
    }

    @Test
    fun alternateSelectionIsDifferentAndBoundedToOneAttempt() {
        val eligible = ApiClient.httpFailure(404, ProviderKind.OPENAI)
        val models = listOf("selected", "alternate", "another")

        assertEquals(
            "alternate",
            ModelFallbackPolicy.selectAlternate("selected", models, eligible, false),
        )
        assertEquals(
            null,
            ModelFallbackPolicy.selectAlternate("selected", models, eligible, true),
        )
        assertEquals(
            null,
            ModelFallbackPolicy.selectAlternate("selected", listOf("selected"), eligible, false),
        )
    }

    @Test
    fun httpErrorsKeepActionableCategorySpecificMessages() {
        assertTrue(ApiClient.httpFailure(401, ProviderKind.GEMINI).message!!.contains("API key"))
        assertTrue(ApiClient.httpFailure(402, ProviderKind.GEMINI).message!!.contains("Billing"))
        assertTrue(ApiClient.httpFailure(429, ProviderKind.GEMINI).message!!.contains("quota"))
        assertTrue(ApiClient.httpFailure(404, ProviderKind.GEMINI).message!!.contains("model"))
    }
}
