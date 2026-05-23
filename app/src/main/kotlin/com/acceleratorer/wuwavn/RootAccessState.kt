package com.acceleratorer.wuwavn

enum class RootAccessState(val label: String) {
    NOT_CHECKED("Root preview not checked"),
    CHECKING("Checking root access"),
    AVAILABLE("Root access detected"),
    NOT_AVAILABLE("Root not detected"),
    DENIED("Root permission denied or timed out"),
    CHECK_FAILED("Root check failed"),
}
