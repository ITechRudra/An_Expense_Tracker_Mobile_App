package com.rudra.expensetracker.core.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BankRegistryTest {

    @Test
    fun `strips DLT prefix and suffix from sender headers`() {
        assertThat(BankRegistry.normaliseSender("AD-INDUSB-S")).isEqualTo("INDUSB")
        assertThat(BankRegistry.normaliseSender("VM-HDFCBK")).isEqualTo("HDFCBK")
        assertThat(BankRegistry.normaliseSender("jd-sbiupi-t")).isEqualTo("SBIUPI")
    }

    @Test
    fun `identifies banks from the sender header`() {
        assertThat(BankRegistry.identify("AD-INDUSB-S", "")?.code).isEqualTo("INDUSIND")
        assertThat(BankRegistry.identify("VK-KOTAKB-S", "")?.code).isEqualTo("KOTAK")
        assertThat(BankRegistry.identify("AD-IDFCFB-S", "")?.code).isEqualTo("IDFC")
    }

    @Test
    fun `falls back to the message body when the sender is unknown`() {
        assertThat(BankRegistry.identify("51969", "Rs 10 debited - Bank of Baroda")?.code)
            .isEqualTo("BOB")
    }

    @Test
    fun `treats long mobile numbers as personal and short codes as commercial`() {
        assertThat(BankRegistry.looksLikeServiceSender("+919876543210")).isFalse()
        assertThat(BankRegistry.looksLikeServiceSender("9876543210")).isFalse()
        assertThat(BankRegistry.looksLikeServiceSender("51969")).isTrue()
        assertThat(BankRegistry.looksLikeServiceSender("AD-HDFCBK-S")).isTrue()
    }
}
