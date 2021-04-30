package montuno

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Test

class TruffleTest {
    @Test
    fun testId() {
        val ctx: Context = Context.create()
        val x = ctx.eval("montuno", "id")
        assert(x.canExecute())
        assert(x.execute(42).asInt() == 42)
    }

    @Test
    fun testConst() {
        val ctx: Context = Context.create()
        val x = ctx.eval("montuno", "const")
        assert(x.canExecute())
        val y = x.execute(42)
        assert(y.canExecute())
        assert(y.execute(5).asInt() == 42)
    }

    @Test
    fun testConstId() {
        val ctx: Context = Context.create()
        val x = ctx.eval("montuno", "const id")
        assert(x.canExecute())
        assert(x.execute("X", 42).asInt() == 42)
    }

    @Test
    fun testEval() {
        val ctx: Context = Context.create()
        val src = "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2))) 15"
        val x = ctx.eval("montuno", src)
        assert(x.asInt() == 42)
    }
}
