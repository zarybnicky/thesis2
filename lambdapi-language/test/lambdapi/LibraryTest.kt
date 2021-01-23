package lambdapi

import com.oracle.truffle.api.Truffle
import org.graalvm.polyglot.Context
import java.util.*
import kotlin.test.Test

class LibraryTest {
    @Test fun testSomeLibraryMethod() {
        Truffle.getRuntime();
        val ctx = Context.create()
        ctx.enter()
        ctx.initialize("lambdapi")

    }
}
