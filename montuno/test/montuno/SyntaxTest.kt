package montuno

import montuno.syntax.*
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class SyntaxTest {
    @Test
    fun testParseExpr() {
        assertEquals(
            parseExpr("let i : Nat = 5 in i"),
            RLet(
                Loc.Range(0, 20),
                "i",
                RNat(Loc.Range(8, 3)),
                RLitNat(Loc.Range(14, 1), 5),
                RVar(Loc.Range(19, 1), "i")
            )
        )
    }

    @Test
    fun testEval() {
        val ctx: Context = Context.create()
        val x = ctx.eval(
            "montuno",
            "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2))) 15"
        )
        assert(x.asInt() == 42)
    }
}
