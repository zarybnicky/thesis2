package lambdapi

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import com.github.h0tk3y.betterParse.utils.Tuple2

//type Env_ = [Value_]
//type Type_     =  Value_
//type Context_    =  [(Name, Type_)]

data class UnknownName(val name: String)
sealed class Name {
    data class Global(val name: String)
    data class Local(val name: Int)
    data class Quote(val name: Int)
}

sealed class Type {
    data class TFree(val name: Name)
    data class Fun(val left: Type, val right: Type)
}

sealed class Stmt<out N> {
    data class Let<out N>(val name: String, val expr: ITerm<N>) : Stmt<N>()
    data class Assume<N>(val xs: List<Tuple2<String, CTerm<N>>>) : Stmt<N>()
    data class Eval<out N>(val expr: ITerm<N>) : Stmt<N>()
    data class PutStrLn<out N>(val str: String) : Stmt<N>()
    data class Out<out N>(val str: String) : Stmt<N>()
}

sealed class CTerm<out N> {
    data class Inf<out N>(val term: ITerm<N>) : CTerm<N>()
    data class Lam<out N>(val name: String, val expr: CTerm<N>) : CTerm<N>()
    class Zero<out N> : CTerm<N>()
    data class Succ<out N>(val num: CTerm<N>) : CTerm<N>()
    data class Nil<out N>(val num: CTerm<N>) : CTerm<N>()
    data class Cons<out N>(val ltyp: CTerm<N>, val lval: CTerm<N>, val rtyp: CTerm<N>, val rval: CTerm<N>) : CTerm<N>()
    data class Refl<out N>(val left: CTerm<N>, val right: CTerm<N>) : CTerm<N>()
    data class FZero<out N>(val term: CTerm<N>) : CTerm<N>()
    data class FSucc<out N>(val term: CTerm<N>, val num: CTerm<N>) : CTerm<N>()
}

sealed class ITerm<out N> {
    data class Ann<out N>(val term: CTerm<N>, val type: CTerm<N>) : ITerm<N>()
    class Star<out N> : ITerm<N>()
    data class Pi<out N>(val bind: CTerm<N>, val value: CTerm<N>) : ITerm<N>()
    data class PiN<out N>(val name: N, val bind: CTerm<N>, val value: CTerm<N>) : ITerm<N>()
    data class Bound(val level: Int) : ITerm<Any>()
    data class Free<out N>(val name: N) : ITerm<N>()
    data class App<out N>(val left: ITerm<N>, val right: CTerm<N>) : ITerm<N>()
    class Nat<out N> : ITerm<N>()
    data class NatElim<out N>(val ltyp: CTerm<N>, val lval: CTerm<N>, val rtyp: CTerm<N>, val rval: CTerm<N>) :
        ITerm<N>()

    data class Vec<out N>(val left: ITerm<N>, val right: CTerm<N>) : ITerm<N>()
    data class VecElim<out N>(
        val lt: CTerm<N>,
        val lv: CTerm<N>,
        val rt: CTerm<N>,
        val rv: CTerm<N>,
        val et: CTerm<N>,
        val ev: CTerm<N>
    ) : ITerm<N>()

    data class Eq<out N>(val left: CTerm<N>, val right: CTerm<N>, val rright: CTerm<N>) : ITerm<N>()
    data class EqElim<out N>(
        val lt: CTerm<N>,
        val lv: CTerm<N>,
        val rt: CTerm<N>,
        val rv: CTerm<N>,
        val et: CTerm<N>,
        val ev: CTerm<N>
    ) : ITerm<N>()

    data class Fin<out N>(val value: CTerm<N>) : ITerm<N>()
    data class FinElim<out N>(
        val ltyp: CTerm<N>,
        val lval: CTerm<N>,
        val rtyp: CTerm<N>,
        val rval: CTerm<N>,
        val elim: CTerm<N>
    ) : ITerm<N>()
}

sealed class Value {
    //    data class VClosure(val env: List<Value>, val name: String, val expr: Expr) : Value()
    data class VLam(val cl: (Value) -> Value) : Value()
    object Star : Value()
    data class VPi(val bind: Value, val cl: (Value) -> Value) : Value()
    data class VNeutral(val neutral: Neutral) : Value()
    object Nat : Value()
    object Zero : Value()
    data class VSucc(val value: Value) : Value()
    data class VNil(val value: Value) : Value()
    data class VCons(val ltyp: Value, val lval: Value, val rtyp: Value, val rval: Value) : Value()
    data class VVec(val typ: Value, val value: Value) : Value()
    data class VEq(val typ: Value, val left: Value, val right: Value) : Value()
    data class VRefl(val left: Value, val right: Value) : Value()
    data class VFZero(val typ: Value) : Value()
    data class VFSucc(val typ: Value, val nat: Value) : Value()
    data class VFin(val typ: Value) : Value()
}

sealed class Neutral {
    data class NFree(val name: Name) : Neutral()
    data class NApp(val neutral: Neutral, val value: Value) : Neutral()
    data class NNatElim(val typ: Value, val zero: Value, val succ: Value, val value: Neutral) : Neutral()
    data class NVecElim(val l: Value, val r: Value, val lt: Value, val lr: Value, val typ: Value, val v: Neutral) :
        Neutral()

    data class NEqElim(val typ: Value, val l: Value, val r: Value, val lt: Value, val rt: Value, val v: Neutral) :
        Neutral()

    data class NFinElim(val typ: Value, val l: Value, val r: Value, val t: Value, val v: Neutral) : Neutral()
}

class LPiGrammar : Grammar<List<Stmt<UnknownName>>>() {
    private val lineComment by regexToken("--.*$", ignore = true)
    private val blockComment by regexToken(Regex("\\{-.*-}", RegexOption.MULTILINE), ignore = true)
    private val ws by regexToken("\\s+", ignore = true)
    private val LAM by literalToken("\\")
    private val LPAR by literalToken("(")
    private val RPAR by literalToken(")")
    private val EQ by literalToken("=")
    private val STAR by literalToken("*")
    private val DOT by literalToken(".")
    private val COLON by literalToken(":")
    private val DCOLON by literalToken("::")
    private val ASSUME by literalToken("assume")
    private val FORALL by literalToken("forall")
    private val PUTSTRLN by literalToken("putStrLn")
    private val OUT by literalToken("out")
    private val LET by literalToken("let")
    private val IF by literalToken("if")
    private val THEN by literalToken("then")
    private val ELSE by literalToken("else")
    private val ARR by literalToken("->")
    private val IDENT by regexToken("[a-zA-Z_][a-zA-Z0-9_']*")
    private val STRING by regexToken("\".*?\"")
    private val NAT by regexToken("[0-9]*?")

    private val let_ by -LET * IDENT * -EQ * parser(this::iterm0) map { (i, t) -> Stmt.Let(i.text, t) }
    private val assume by -ASSUME * parser(this::binds) map { Stmt.Assume(it) }
    private val putStrLn by -PUTSTRLN * STRING map { Stmt.PutStrLn<UnknownName>(it.text.removeSurrounding("\"", "\"")) }
    private val out by -OUT * STRING map { Stmt.Out<UnknownName>(it.text.removeSurrounding("\"", "\"")) }
    private val stmt: Parser<Stmt<UnknownName>> by let_ or assume or putStrLn or out

    private val lam by -LAM * oneOrMore(IDENT) * -ARR * parser(this::cterm0) map
            { (i, t) -> i.foldRight(t, { x, acc -> CTerm.Lam<UnknownName>(x.text, acc) }) }
    private val parLam by -LPAR * parser(this::lam) * -RPAR

    val bind: Parser<Tuple2<String, CTerm<UnknownName>>> by (IDENT map { it.text }) * -DCOLON * parser(this::cterm0)
    val binds: Parser<List<Tuple2<String, CTerm<UnknownName>>>> by
    oneOrMore(-LPAR * parser(this::bind) * -RPAR) or (parser(this::bind) map { listOf(it) })

    val cterm0: Parser<CTerm<UnknownName>> by lam or (parser(this::iterm0) map { CTerm.Inf(it) })
    val cterm1: Parser<CTerm<UnknownName>> by parLam or (parser(this::iterm1) map { CTerm.Inf(it) })
    val cterm2: Parser<CTerm<UnknownName>> by parLam or (parser(this::iterm2) map { CTerm.Inf(it) })
    val cterm3: Parser<CTerm<UnknownName>> by parLam or (parser(this::iterm3) map { CTerm.Inf(it) })
    val iterm0: Parser<ITerm<UnknownName>> by
    (-FORALL * parser(this::binds) * -DOT * parser(this::cterm0) map { (bs, t) ->
        bs.drop(1).foldRight(
            ITerm.PiN(UnknownName(bs[0].t1), bs[0].t2, t),
            { x, acc -> ITerm.PiN(UnknownName(x.t1), x.t2, CTerm.Inf(acc)) })
    }) or
            (parser(this::parLam) * -ARR * parser(this::cterm0) map { (a, b) -> ITerm.Pi<UnknownName>(a, b) }) or
            (parser(this::iterm1) * -ARR * parser(this::cterm0) map { (a, b) ->
                ITerm.Pi<UnknownName>(CTerm.Inf(a), b)
            }) or parser(this::iterm1)
    val iterm1: Parser<ITerm<UnknownName>> by
    (parser(this::iterm2) * -DCOLON * parser(this::cterm0) map { (a, b) -> ITerm.Ann(CTerm.Inf(a), b) }) or
            parser(this::iterm2) or
            (-LPAR * parser(this::lam) * -DCOLON * parser(this::cterm0) * -RPAR map { (a, b) -> ITerm.Ann(a, b) })
    val iterm2: Parser<ITerm<UnknownName>> by
    parser(this::iterm3) * zeroOrMore(parser(this::cterm3)) map { (t, ts) ->
        ts.fold(t, { acc, x -> ITerm.App(acc, x) })
    }
    val iterm3: Parser<ITerm<UnknownName>> by
    (STAR map { ITerm.Star<UnknownName>() }) or
            (NAT map { ITerm.Ann(toNat(it.text), CTerm.Inf(ITerm.Nat())) }) or
            (IDENT map { ITerm.Free(UnknownName(it.text)) }) or
            -LPAR * parser(this::iterm0) * -RPAR

    override val rootParser: Parser<List<Stmt<UnknownName>>> by zeroOrMore(stmt)
}

fun toNat(num: String): CTerm<UnknownName> {
    var ret = CTerm.Zero<UnknownName>() as CTerm<UnknownName>;
    for (i in 1..num.toInt()) {
        ret = CTerm.Succ(ret)
    }
    return ret
}

fun main() {
    val expr = "let id    = (\\ a x -> x) :: forall (a :: *) . a -> a "
//    val expr = "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if eq x 1 then x else f (printId (if (eq (mod x 2) 0) then div x 2 else plus (mult 3 x) 1))) 11111"
    println(LPiGrammar().parseToEnd(expr))
}
