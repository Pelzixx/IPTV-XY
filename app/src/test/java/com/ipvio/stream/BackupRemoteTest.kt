package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRemoteTest {
    @Test fun dropbox_link_wird_zum_download() {
        assertEquals("https://www.dropbox.com/s/abc/ipvio_backup.json?dl=1", BackupRemote.directLink("https://www.dropbox.com/s/abc/ipvio_backup.json?dl=0"))
    }

    @Test fun google_drive_link_wird_zum_download() {
        assertEquals("https://drive.google.com/uc?export=download&id=XYZ123", BackupRemote.directLink("https://drive.google.com/file/d/XYZ123/view?usp=sharing"))
    }

    @Test fun normale_adresse_bleibt() {
        assertEquals("https://example.org/b.json", BackupRemote.directLink(" https://example.org/b.json "))
    }
}
