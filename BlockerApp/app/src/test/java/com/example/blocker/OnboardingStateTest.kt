package com.example.blocker

import com.example.blocker.core.OnboardingState
import com.example.blocker.core.OnboardingState.StepId
import com.example.blocker.core.OnboardingState.StepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    private fun signals(api: Int, installer: String?, manufacturer: String = "samsung") =
        OnboardingState.DeviceSignals(api, installer, manufacturer)

    private fun verified(
        a11y: Boolean = false,
        notifications: Boolean = false,
        battery: Boolean = false,
        exactAlarms: Boolean = false,
        hasConfig: Boolean = false,
        api: Int = 34
    ) = OnboardingState.VerifiedStates(a11y, notifications, battery, exactAlarms, hasConfig, api)

    @Test fun restrictedLikelyOnlyForSideloaded13Plus() {
        assertTrue(OnboardingState.restrictedSettingsLikely(signals(33, null)))
        assertTrue(OnboardingState.restrictedSettingsLikely(signals(34, "com.android.chrome")))
        assertFalse(OnboardingState.restrictedSettingsLikely(signals(33, "com.android.vending")))
        assertFalse(OnboardingState.restrictedSettingsLikely(signals(33, "ir.mservices.market")))
        assertFalse(OnboardingState.restrictedSettingsLikely(signals(34, "com.farsitel.bazaar")))
        assertFalse(OnboardingState.restrictedSettingsLikely(signals(32, null)))
        assertFalse(OnboardingState.restrictedSettingsLikely(signals(30, "com.android.chrome")))
    }

    @Test fun sideloaded33ShowsRestrictedFirst() {
        val steps = OnboardingState.buildSteps(signals(33, null), verified())
        val visible = OnboardingState.visibleSteps(steps)
        assertEquals(StepId.RESTRICTED_SETTINGS, visible.first().id)
        assertEquals(StepStatus.ACTION_REQUIRED, visible.first().status)
        assertEquals(StepId.RESTRICTED_SETTINGS, OnboardingState.currentStep(steps)?.id)
    }

    @Test fun storeInstallSkipsRestricted() {
        val steps = OnboardingState.buildSteps(signals(33, "com.android.vending", "Google"), verified())
        val byId = steps.associateBy { it.id }
        assertEquals(StepStatus.NOT_APPLICABLE, byId[StepId.RESTRICTED_SETTINGS]?.status)
        assertEquals(StepId.ACCESSIBILITY, OnboardingState.currentStep(steps)?.id)
    }

    @Test fun a11yOnHidesRestrictedAndMarksDone() {
        val steps = OnboardingState.buildSteps(signals(34, null), verified(a11y = true))
        val byId = steps.associateBy { it.id }
        assertEquals(StepStatus.NOT_APPLICABLE, byId[StepId.RESTRICTED_SETTINGS]?.status)
        assertEquals(StepStatus.DONE, byId[StepId.ACCESSIBILITY]?.status)
    }

    @Test fun oldAndroidHidesVersionGatedSteps() {
        val steps = OnboardingState.buildSteps(signals(30, null, "Google"), verified(api = 30))
        val byId = steps.associateBy { it.id }
        assertEquals(StepStatus.NOT_APPLICABLE, byId[StepId.RESTRICTED_SETTINGS]?.status)
        assertEquals(StepStatus.NOT_APPLICABLE, byId[StepId.NOTIFICATIONS]?.status)
        assertEquals(StepStatus.NOT_APPLICABLE, byId[StepId.EXACT_ALARMS]?.status)
    }

    @Test fun allDoneHasNoCurrentStep() {
        val steps = OnboardingState.buildSteps(
            signals(34, "com.android.vending", "Google"),
            verified(a11y = true, notifications = true, battery = true, exactAlarms = true, hasConfig = true)
        )
        assertNull(OnboardingState.currentStep(steps))
        assertEquals("5 از 5 قدم کامل شده", OnboardingState.progressText(steps))
    }

    @Test fun samsungDetection() {
        assertTrue(OnboardingState.isSamsung(signals(34, null, "samsung")))
        assertTrue(OnboardingState.isSamsung(signals(34, null, "SAMSUNG")))
        assertFalse(OnboardingState.isSamsung(signals(34, null, "Google")))
    }

    @Test fun selectCurrentRespectsSkipAndFocus() {
        fun step(id: OnboardingState.StepId, status: OnboardingState.StepStatus) =
            OnboardingState.SetupStep(id, id.name, required = false, status = status)
        val visible = listOf(
            step(OnboardingState.StepId.ACCESSIBILITY, OnboardingState.StepStatus.ACTION_REQUIRED),
            step(OnboardingState.StepId.BATTERY, OnboardingState.StepStatus.ACTION_REQUIRED),
            step(OnboardingState.StepId.EXACT_ALARMS, OnboardingState.StepStatus.ACTION_REQUIRED)
        )
        // بدون فوکوس و رد کردن: اولین اقدام لازم.
        assertEquals(
            OnboardingState.StepId.ACCESSIBILITY,
            OnboardingState.selectCurrent(visible, emptySet(), null)?.id
        )
        // رد کردن همه‌ی باقی‌مانده‌ها → null (صفحه‌ی پایان)، نه بازگشت قدم ردشده.
        assertNull(
            OnboardingState.selectCurrent(
                visible, setOf(
                    OnboardingState.StepId.ACCESSIBILITY,
                    OnboardingState.StepId.BATTERY,
                    OnboardingState.StepId.EXACT_ALARMS
                ), null
            )
        )
        // رد کردن جزئی: اولین ردنشده.
        assertEquals(
            OnboardingState.StepId.BATTERY,
            OnboardingState.selectCurrent(visible, setOf(OnboardingState.StepId.ACCESSIBILITY), null)?.id
        )
        // فوکوس روی قدم اقدام لازم، بر ترتیب غلبه می‌کند.
        assertEquals(
            OnboardingState.StepId.EXACT_ALARMS,
            OnboardingState.selectCurrent(visible, emptySet(), OnboardingState.StepId.EXACT_ALARMS)?.id
        )
        // فوکوس روی قدم کامل نادیده گرفته می‌شود (نمایش آن با شاخه‌ی جداست).
        val withDone = listOf(
            step(OnboardingState.StepId.ACCESSIBILITY, OnboardingState.StepStatus.DONE),
            step(OnboardingState.StepId.BATTERY, OnboardingState.StepStatus.ACTION_REQUIRED)
        )
        assertEquals(
            OnboardingState.StepId.BATTERY,
            OnboardingState.selectCurrent(withDone, emptySet(), OnboardingState.StepId.ACCESSIBILITY)?.id
        )
    }

    @Test fun troubleshootingExistsForEveryStep() {
        for (step in StepId.values()) {
            assertTrue(step.name, OnboardingState.troubleshooting(step, true).isNotEmpty())
            assertTrue(step.name, OnboardingState.troubleshooting(step, false).isNotEmpty())
        }
    }
}
