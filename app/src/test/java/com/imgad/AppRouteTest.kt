package com.imgad

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {
    @Test
    fun routeArgumentsAreEncodedAndNullCreateResetsDraft() {
        assertEquals("create?sessionId=a%2Fb%3Fc", createSessionRoute("a/b?c"))
        assertEquals("create?sessionId=a%20b~%2A", createSessionRoute("a b~*"))
        assertEquals("providerEditor?providerId=null", providerEditorRoute(null))
        assertEquals("modelEditor?providerId=p%2F1&modelId=null", modelEditorRoute("p/1", null))
    }
}
