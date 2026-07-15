package com.imgad.data.remote

import com.imgad.domain.model.GenerationFailure
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUrlPolicyTest {
    @Test
    fun defaultPolicyRejectsInsecureLoopbackAndPrivateHosts() {
        listOf(
            "http://localhost/v1",
            "https://127.0.0.1/v1",
            "https://169.254.169.254/latest",
            "https://100.64.0.1/v1",
            "https://[fc00::1]/v1",
        ).forEach { url ->
            var failure: Throwable? = null
            try {
                RemoteUrlPolicy().validate(url)
            } catch (error: Throwable) {
                failure = error
            }
            assertTrue(failure is GenerationFailure)
        }
    }

    @Test
    fun localHttpCanOnlyBeEnabledExplicitly() {
        RemoteUrlPolicy(allowInsecureHttp = true, allowLoopback = true).validate("http://localhost:8080/v1")
    }

    @Test
    fun urlValidationDoesNotPerformDnsResolution() {
        RemoteUrlPolicy().validate("https://this-host-does-not-exist.invalid/v1")
    }

    @Test
    fun acceptsPublicHttpsAndRejectsDnsAddresses() {
        RemoteUrlPolicy().validate("https://api.example.com/v1")
        val dns = ValidatingDns(
            RemoteUrlPolicy(),
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getByName("100.64.0.1"))
            },
        )
        var failure: Throwable? = null
        try {
            dns.lookup("rebound.example")
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue(failure is GenerationFailure)
    }

    @Test
    fun rejectsBaseQueryAndFragment() {
        val policy = RemoteUrlPolicy()
        var failure: Throwable? = null
        try {
            policy.validate("https://api.example.com/v1?key=x", rejectQueryFragment = true)
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue(failure is GenerationFailure)
    }
}
