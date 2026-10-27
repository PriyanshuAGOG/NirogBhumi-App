package com.nirogbhumi.app.health.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadTypesTest {
    @Test fun `the Health File pdf with no reported type is declared as a pdf`() {
        assertEquals("application/pdf", UploadTypes.resolve(null, "Nirog-Health-File.pdf", "health-file"))
        assertEquals("application/pdf", UploadTypes.resolve("application/octet-stream", null, "health-file"))
        assertEquals("application/pdf", UploadTypes.resolve("", "", "health-file"))
    }

    @Test fun `a type Android reports is kept`() {
        assertEquals("image/png", UploadTypes.resolve("image/png", "x.bin", "lab-reports"))
        assertEquals("application/pdf", UploadTypes.resolve("application/pdf", null, "lab-reports"))
    }

    @Test fun `the file name decides when Android reports nothing useful`() {
        assertEquals("image/jpeg", UploadTypes.resolve(null, "scan.JPG", "lab-reports"))
        assertEquals("application/pdf", UploadTypes.resolve("*/*", "report.pdf", "lab-reports"))
        assertEquals("image/heic", UploadTypes.resolve(null, "IMG_1.heic", "profile-photo"))
    }

    @Test fun `with no clue a photo folder defaults to a jpeg`() {
        assertEquals("image/jpeg", UploadTypes.resolve(null, null, "profile-photo"))
        assertEquals("image/jpeg", UploadTypes.resolve(null, "noextension", "lab-reports"))
    }

    @Test fun `only photos and pdfs are allowed, matching the storage rule`() {
        assertTrue(UploadTypes.isAllowed("application/pdf"))
        assertTrue(UploadTypes.isAllowed("image/jpeg")); assertTrue(UploadTypes.isAllowed("image/HEIC"))
        assertFalse(UploadTypes.isAllowed("application/octet-stream"))
        assertFalse(UploadTypes.isAllowed("image/svg+xml"))
        assertFalse(UploadTypes.isAllowed("text/html"))
        assertFalse(UploadTypes.isAllowed("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }
}
