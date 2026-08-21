package com.acceleratorer.wuwavn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WuWa36LayoutTest {
    @Test
    fun resolvesNewestResourceVersionInTheGameSeries() {
        val resolved = WuWa36Layout.resolveResourceVersion(
            gameVersion = "3.6.0",
            resourceVersions = listOf("3.5.9", "3.6.0", "3.6.1", "4.0.0", "junk"),
            versionsWithManifest = setOf("3.6.0", "3.6.1", "4.0.0"),
        )

        assertEquals("3.6.1", resolved)
    }

    @Test
    fun resolvesNewestLanguageManifestAndFallsBackToBase() {
        assertEquals(
            "3.6.4",
            WuWa36Layout.resolveLanguageVersion(
                listOf("ManifestLang_en_3.6.1.txt", "ManifestLang_en_3.6.4.txt", "bad.txt"),
            ),
        )
        assertEquals("Base", WuWa36Layout.resolveLanguageVersion(emptyList()))
    }

    @Test
    fun buildsOnlyTheDynamic36Targets() {
        val target = WuWa36Layout.targets("3.6.1", "3.6.4")

        assertEquals(
            "UE4Game/Client/Client/Saved/Resources/3.6.1/Lang_en/3.6.4/WuWaVH_99_P.pak",
            target.pakRelativePath,
        )
        assertEquals(
            "UE4Game/Client/Client/Saved/Resources/3.6.1/Lang_en/3.6.4/WuWaVH_99_P.sig",
            target.sigRelativePath,
        )
        assertTrue(WuWa36Layout.isAllowedDynamicPath(target.pakRelativePath))
        assertTrue(WuWa36Layout.isAllowedDynamicPath(target.sigRelativePath))
        assertFalse(WuWa36Layout.isAllowedDynamicPath("UE4Game/Client/Content/Paks/WuWaVH_99_P.pak"))
        assertFalse(WuWa36Layout.isAllowedDynamicPath("UE4Game/Client/Client/Saved/Resources/3.6.1/../../secret"))
    }

    @Test
    fun patchesMountLangWithSixFieldRegistryLineAndPreservesOtherEntries() {
        val original = """
            ::Mount::
            Lang_en/3.6.4/Base.pak,1,ORIGINAL,,,
            Lang_en/3.5.9/WuWaVH_99_P.pak,99,OLDPAK,OLDSIG,,
            Lang_en/3.6.4/WuWaVH_99_P.pak,99,STALEPAK,STALESIG,,
            ::Del::
        """.trimIndent() + "\n"

        val patched = WuWa36Layout.patchMountLang(
            original = original,
            langVersion = "3.6.4",
            pakSha1 = "0123456789abcdef0123456789abcdef01234567",
            sigSha1 = "89abcdef0123456789abcdef0123456789abcdef",
        )

        assertTrue(patched.contains("Lang_en/3.6.4/Base.pak,1,ORIGINAL,,,"))
        assertFalse(patched.contains("OLDPAK"))
        assertFalse(patched.contains("STALEPAK"))
        assertTrue(
            patched.contains(
                "Lang_en/3.6.4/WuWaVH_99_P.pak,99,0123456789ABCDEF0123456789ABCDEF01234567,89ABCDEF0123456789ABCDEF0123456789ABCDEF,,",
            ),
        )
        assertEquals("::Mount::", patched.lineSequence().first())
        assertEquals("::Del::", patched.lineSequence().last { it.isNotBlank() })
    }

    @Test
    fun rejectsMountLangWithoutMarkersOrInvalidHashes() {
        assertFalse(WuWa36Layout.isValidMountLang("Lang_en/Base/x.pak,1,abc,,,"))

        val invalid = runCatching {
            WuWa36Layout.patchMountLang(
                original = "::Mount::\n::Del::\n",
                langVersion = "3.6.4",
                pakSha1 = "not-a-sha1",
                sigSha1 = "0123456789abcdef0123456789abcdef01234567",
            )
        }
        assertTrue(invalid.isFailure)
    }

    @Test
    fun requiresARegisteredPatchLineForInstallAndRejectsItForOriginalMount() {
        val original = "::Mount::\nLang_en/Base/base.pak,1,0123456789abcdef0123456789abcdef01234567,89abcdef0123456789abcdef0123456789abcdef,,\n::Del::\n"
        val patched = WuWa36Layout.patchMountLang(
            original,
            "3.6.4",
            "0123456789abcdef0123456789abcdef01234567",
            "89abcdef0123456789abcdef0123456789abcdef",
        )

        assertTrue(WuWa36Layout.containsPatchRegistryLine(patched, "3.6.4"))
        assertFalse(WuWa36Layout.containsPatchRegistryLine(original, "3.6.4"))
    }

    @Test
    fun rejectsAnyPatchedMountLangAsAnOriginalBackup() {
        val original = "::Mount::\nLang_en/Base/base.pak,1,0123456789abcdef0123456789abcdef01234567,89abcdef0123456789abcdef0123456789abcdef,,\n::Del::\n"
        val patchedForAnotherLanguage = WuWa36Layout.patchMountLang(
            original,
            "3.6.1",
            "0123456789abcdef0123456789abcdef01234567",
            "89abcdef0123456789abcdef0123456789abcdef",
        )

        assertTrue(WuWa36Layout.isOriginalMountLang(original))
        assertFalse(WuWa36Layout.isOriginalMountLang(patchedForAnotherLanguage))
        assertTrue(WuWa36Layout.containsAnyPatchRegistryEntry(patchedForAnotherLanguage))
    }

    @Test
    fun installedStateRequiresPakSigAndRegistryTogether() {
        assertEquals(
            PatchInstallState.PATCHED,
            InstalledStateDetector.resolvePatchState(true, true, true, true),
        )
        assertEquals(
            PatchInstallState.UNKNOWN,
            InstalledStateDetector.resolvePatchState(true, false, true, true),
        )
        assertEquals(
            PatchInstallState.UNKNOWN,
            InstalledStateDetector.resolvePatchState(true, true, true, false),
        )
        assertEquals(
            PatchInstallState.UNKNOWN,
            InstalledStateDetector.resolvePatchState(false, false, true, false, mountLangValid = false),
        )
        assertEquals(
            PatchInstallState.ORIGINAL,
            InstalledStateDetector.resolvePatchState(false, false, true, false),
        )
    }

    @Test
    fun doesNotAllowLegacyPatchTargetsIn36Transaction() {
        assertFalse(WuWa36Layout.isPatchPakPath(
            "UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak",
        ))
        assertFalse(WuWa36Layout.isPatchSigPath(
            "UE4Game/Client/Client/Saved/Resources/3.5.9/Lang_en/Base/WuWaVH_99_P.sig",
        ))
    }
}
