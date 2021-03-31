package montuno

import com.oracle.truffle.api.Truffle
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Test

val ctx: Context = Context.create()

class LibraryTest {
  @Test
  fun testSomeLibraryMethod() {
    Truffle.getRuntime()
    val x = ctx.eval("montuno", "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2))) 15")
    assert(x.asInt() == 42)
  }
}
