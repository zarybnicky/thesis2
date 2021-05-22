package montuno

import org.graalvm.polyglot.Context
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

fun makeCtx(): Context = Context.newBuilder().allowExperimentalOptions(true).build()

class TruffleTest {
    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testNormalizeId(lang: String) = makeCtx().use { ctx ->
        val x = ctx.eval(lang, "id : {A}->A->A = \\x.x; {-# NORMALIZE id #-}")
        assert(x.asString() == "λ {A} x. x")
    }
    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testNormalizeIdPartiallyApplied(lang: String) = makeCtx().use { ctx ->
        val x = ctx.eval(lang, "id : {A}->A->A = \\x.x; {-# NORMALIZE id {*} #-}")
        assert(x.asString() == "λ x. x")
    }
    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testNormalizeIdFullyApplied(lang: String) = makeCtx().use { ctx ->
        val x = ctx.eval(lang, "id : {A}->A->A = \\x.x; {-# NORMALIZE id {*} * #-}")
        assert(x.asString() == "*")
    }

    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testPolyglotClosure(lang: String) = makeCtx().use { ctx ->
        assert(ctx.eval(lang, "\\x.x").execute(5).asInt() == 5)
    }

    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testCompile3(lang: String) = makeCtx().use { ctx ->
        val x = ctx.eval(lang, "id:{A}->A->A=\\x.x; const:{A B}->A->B->A=\\x y.x;{-# NORMALIZE const id #-}")
        assert(x.asString() == "λ {A} x. x")
    }
    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testConst(lang: String) = makeCtx().use { ctx ->
        assert(ctx.eval(lang, "const").execute(5, 42).asInt() == 5)
    }
    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testConstId(lang: String) = makeCtx().use { ctx ->
        assert(ctx.eval(lang, "const id").execute(42, 5).asInt() == 5)
    }
    @ParameterizedTest @ValueSource(strings = ["montuno-pure","montuno"])
    fun testEval(lang: String) {
        val ctx: Context = Context.create()
        val src = "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2))) 15"
        val x = ctx.eval(lang, src)
        assert(x.asInt() == 42)
    }
}
